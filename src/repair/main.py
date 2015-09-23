import signal
import os
import shutil
import argparse
import time
import json

from project import Validation, Frontend, Backend, Golden
from utils import format_time, Dump, Trace, Tester
from transformation import RepairableTransformer, SuspiciousTransformer, FixInjector
from localization import Localizer
from reduction import Reducer
from inference import Inferrer
from synthesis import Synthesizer


class Angelix:

    def __init__(self, working_dir, src, buggy, oracle, tests, golden, dump, **kwargs):
        with open(tests) as tests_file:
            tests_spec = json.load(tests_file)
        self.test_suite = [t['id'] for t in tests_spec]
        self.initial_tests = kwargs['initial_tests']

        extracted = os.path.join(working_dir, 'extracted')
        os.mkdir(self.dir)

        self.run_test = Tester(self.config, oracle)
        self.groups_of_suspicious = Localizer(self.config, lines, kwargs['localization'])
        self.reduce = Reducer(self.config)
        self.infer_specification = Inferrer(self.config)
        self.synthesize_fix = Synthesizer(self.config, extracted)
        self.instrument_for_localization = RepairableTransformer(self.config)
        self.instrument_for_inference = SuspiciousTransformer(self.config, extracted)
        self.apply_fix = FixInjector(self.config)

        validation_dir = os.path.join(working_dir, "validation")
        shutil.copytree(src, validation_dir)
        self.validation_src = Validation(validation_dir, buggy, make_args, tests_spec)
        self.validation_src.build_compilation_db()

        def init_repair_src(src, dst):
            shutil.copytree(src, dst)
            shutil.copy(os.path.join(validation_dir, 'compile_commands.json'), dst)

        frontend_dir = os.path.join(working_dir, "frontend")
        init_repair_src(src, frontend_dir)
        self.frontend_src = Frontend(frontend_dir, buggy, make_args, tests_spec)
        
        backend_dir = os.path.join(working_dir, "backend")
        init_repair_src(src, backend_dir)
        self.backend_src = Backend(backend_dir, buggy, make_args, tests_spec)
        
        if golden is not None:
            golden_dir = os.path.join(working_dir, "golden")
            init_repair_src(golden, golden_dir)
            self.golden_src = Golden(golden_dir, buggy, make_args, tests_spec)

        self.dump = Dump(working_dir, dump)
        self.trace = Trace(working_dir)

    def generate_patch(self):

        def evaluate(src):
            positive = []
            negative = []
            for test in self.test_suite:
                src.build_test(test)
                if self.run_test(src, test):
                    positive.append(test)
                else:
                    negative.append(test)
            return positive, negative
               
        self.validation_src.build()
        positive, negative = evaluate(self.validation_src)
        
        self.instrument_for_localization(self.frontend_src)
        self.frontend_src.build()
        for test in positive:
            self.frontend_src.build_test(test)
            self.trace += test
            if test not in self.dump:
                self.dump += test
                self.run_test(self.frontend_src, test, dump=self.dump[test], trace=self.trace[test])
            else:
                self.run_test(self.frontend_src, test, trace=self.trace[test])

        if self.golden_src is not None:
            self.golden_src.build()
            
        for test in negative:
            self.frontend_src.build_test(test)
            self.trace += test
            self.run_test(self.frontend_src, test, trace=self.trace[test])
            if test not in self.dump:
                if self.golden_src is None:
                    raise Exception("error: golden version or correct dump is needed for the failing test {}".format(test))
                self.golden_src.build_test(test)
                self.dump += test
                self.run_test(self.golden_src, test, dump=self.dump[test])
        
        suspicious = self.groups_of_suspicious(self.trace.parse())

        while len(negative) > 0 and len(suspicous) > 0:
            expressions = suspicious.pop()
            repair_suite = self.reduce(positive, negative, expressions, self.initial_tests)
            self.backend_src.restore_buggy()
            self.instrument_for_inference(self.backend_src, expressions)
            angelic_forest = dict()
            for test in repair_suite:
                angelic_forest[test] = self.infer_specification(self.backend_src, test)
            initial_fix = self.synthesize_fix(angelic_forest)
            if initial_fix is None:
                continue
            self.validation_src.restore_buggy()
            self.apply_fix(self.validation_src, initial_fix)
            pos, neg = evaluate(self.validation_src)
            assert set(neg).isdisjoint(set(repair_suite)), "error: wrong fix generated"
            positive, negative = pos, neg

            while len(negative) > 0:
                counterexample = negative.pop()
                repair_suite.append(counterexample)
                angelic_forest[counterexample] = self.infer_specification(self.backend_src, counterexample)
                fix = self.synthesize_fix(angelic_forest)
                if fix is None:
                    break
                self.validation_src.restore_buggy()
                self.apply_fix(self.validation_src, fix)
                pos, neg = evaluate(self.validation_src)
                assert set(neg).isdisjoint(set(repair_suite)), "error: wrong fix generated"
                positive, negative = pos, neg
 
        if len(negative) > 0:
            return None
        else:
            return self.validation_src.diff_buggy()


if __name__ == "__main__":

    parser = argparse.ArgumentParser('angelix')
    parser.add_argument('src', help='source directory')
    parser.add_argument('buggy', help='relative path to buggy file')
    parser.add_argument('oracle', help='oracle script')
    parser.add_argument('tests', help='tests JSON database')
    parser.add_argument('--golden', metavar='DIR', help='golden source directory')
    parser.add_argument('--dump', metavar='DIR', help='correct dump for failing test cases')
    parser.add_argument('--lines', metavar='LINE', nargs='*', help='suspicious lines')
    parser.add_argument('--timeout', metavar='MS', type=int, default=100000,
                        help='total repair timeout (default: %(default)s)')
    parser.add_argument('--initial-tests', metavar='NUM', type=int, default=3,
                        help='initial repair test suite size (default: %(default)s)')
    parser.add_argument('--conditions-only', action='store_true',
                        help='repair only conditions (default: %(default)s)')
    parser.add_argument('--test-timeout', metavar='MS', type=int, default=10000,
                        help='test case timeout (default: %(default)s)')
    parser.add_argument('--suspicious', metavar='NUM', type=int, default=5,
                        help='number of suspicious repaired at ones (default: %(default)s)')
    parser.add_argument('--iterations', metavar='NUM', type=int, default=4,
                        help='max number of iterations through suspicious (default: %(default)s)')
    parser.add_argument('--localization', metavar='FORMULA', default='jaccard',
                        help='formula for localization algorithm (default: %(default)s)')
    parser.add_argument('--klee-forks', metavar='NUM', type=int, default=1000,
                        help='KLEE max number of forks (default: %(default)s)')
    parser.add_argument('--klee-timeout', metavar='MS', type=int, default=0,
                        help='KLEE timeout (default: %(default)s)')
    parser.add_argument('--klee-solver-timeout', metavar='MS', type=int, default=0,
                        help='KLEE solver timeout (default: %(default)s)')
    parser.add_argument('--synthesis-timeout', metavar='MS', type=int, default=10000,
                        help='synthesis timeout (default: %(default)s)')
    parser.add_argument('--synthesis-levels', metavar='LEVEL', nargs='*',
                        default=['alternative', 'integer', 'boolean', 'comparison'],
                        help='component levels (default: alternative integer boolean comparison)')

    args = parser.parse_args()

    working_dir = os.path.join(os.getcwd(), ".angelix")
    if os.path.exists(working_dir):
        shutil.rmtree(working_dir)
        os.makedirs(working_dir)

    tool = Angelix(working_dir,
                   src = args.src,
                   buggy = args.buggy,
                   oracle = args.oracle,
                   tests = args.tests,
                   golden = args.golden,
                   dump = args.dump,
                   lines = args.lines,
                   initial_tests = args.initial_tests,
                   conditions_only = args.conditions_only,
                   test_timeout = args.test_timeout,
                   suspicious = args.suspicious,
                   iterations = args.iterations,
                   localization = args.localization,
                   klee_forks = args.klee_forks,
                   klee_timeout = args.klee_timeout,
                   klee_solver_timeout = args.klee_solver_timeout,
                   synthesis_timeout = args.synthesis_timeout,
                   synthesis_levels = args.synthesis_levels)

    start = time.time()
    with time_limit(args.timeout ):
        patch = tool.generate_patch()
    except TimeoutException:
        print("failed to generate patch (timeout)")
        exit(1)
    end = time.time()
    elapsed = format_time(end - start)    
    
    if patch is None:
        print("no patch is generated in {}".format(elapsed))
    else:
        print("patch is successfully generated in {} (see generated.diff)".format(elapsed))
        with open('generated.diff', 'w+') as file:
            for line in patch:
                file.write(line)
