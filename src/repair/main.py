import signal
import os
import shutil
import argparse
import time
import json
import logging

from project import Validation, Frontend, Backend, Golden
from utils import format_time, time_limit, TimeoutException, Dump, Trace
from transformation import RepairableTransformer, SuspiciousTransformer, FixInjector
from testing import Tester
from localization import Localizer
from reduction import Reducer
from inference import Inferrer
from synthesis import Synthesizer


logger = logging.getLogger(__name__)


class Angelix:

    def __init__(self, working_dir, src, buggy, oracle, tests, golden, output, lines, build, config):
        self.config = config
        self.test_suite = tests.keys()
        extracted = os.path.join(working_dir, 'extracted')
        os.mkdir(extracted)

        self.run_test = Tester(config, oracle)
        self.groups_of_suspicious = Localizer(config, lines)
        self.reduce = Reducer(config)
        self.infer_spec = Inferrer(config)
        self.synthesize_fix = Synthesizer(config, extracted)
        self.instrument_for_localization = RepairableTransformer(config)
        self.instrument_for_inference = SuspiciousTransformer(config, extracted)
        self.apply_patch = FixInjector(config)

        validation_dir = os.path.join(working_dir, "validation")
        shutil.copytree(src, validation_dir)
        self.validation_src = Validation(validation_dir, buggy, build, tests)
        compilation_db = self.validation_src.export_compilation_db()
        self.validation_src.import_compilation_db(compilation_db)

        frontend_dir = os.path.join(working_dir, "frontend")
        shutil.copytree(src, frontend_dir)
        self.frontend_src = Frontend(frontend_dir, buggy, build, tests)
        self.frontend_src.import_compilation_db(compilation_db)
        
        backend_dir = os.path.join(working_dir, "backend")
        shutil.copytree(src, backend_dir)
        self.backend_src = Backend(backend_dir, buggy, build, tests)
        self.backend_src.import_compilation_db(compilation_db)
        
        if golden is not None:
            golden_dir = os.path.join(working_dir, "golden")
            shutil.copytree(golden, golden_dir)
            self.golden_src = Golden(golden_dir, buggy, build, tests)
            self.golden_src.import_compilation_db(compilation_db)
        else:
            self.golden_src = None

        self.dump = Dump(working_dir, output)
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
        logger.info('running positive tests for debugging')
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

        logger.info('running negative tests for debugging')
        for test in negative:
            self.frontend_src.build_test(test)
            self.trace += test
            self.run_test(self.frontend_src, test, trace=self.trace[test])
            if test not in self.dump:
                if self.golden_src is None:
                    raise Exception("error: golden version or correct output is needed for test {}".format(test))
                self.golden_src.build_test(test)
                self.dump += test
                logger.info('running golden version with test {}'.format(test))
                self.run_test(self.golden_src, test, dump=self.dump[test])

        positive_traces = [(test, self.trace.parse(test)) for test in positive]
        negative_traces = [(test, self.trace.parse(test)) for test in negative]
        suspicious = self.groups_of_suspicious(positive_traces, negative_traces)

        while len(negative) > 0 and len(suspicious) > 0:
            expressions = suspicious.pop()
            repair_suite = self.reduce(positive_traces, negative_traces, expressions)
            self.backend_src.restore_buggy()
            for e in expressions:
                logger.info('considering suspicious expression {}'.format(e))
            self.instrument_for_inference(self.backend_src, expressions)
            angelic_forest = dict()
            for test in repair_suite:
                angelic_forest[test] = self.infer_spec(self.backend_src, test)
            initial_fix = self.synthesize_fix(angelic_forest)
            if initial_fix is None:
                logger.info('cannot synthesize fix')
                continue
            logger.info('candidate fix is synthesized')
            self.validation_src.restore_buggy()
            self.apply_patch(self.validation_src, initial_fix)
            pos, neg = evaluate(self.validation_src)
            assert set(neg).isdisjoint(set(repair_suite)), "error: wrong fix generated"
            positive, negative = pos, neg

            while len(negative) > 0:
                counterexample = negative.pop()
                logger.info('counterexample test is {}'.format(counterexample))
                repair_suite.append(counterexample)
                angelic_forest[counterexample] = self.infer_spec(self.backend_src, counterexample)
                fix = self.synthesize_fix(angelic_forest)
                if fix is None:
                    logger.info('cannot refine fix')
                    break
                logger.info('refined fix is synthesized')
                self.validation_src.restore_buggy()
                self.apply_patch(self.validation_src, fix)
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
    parser.add_argument('--output', metavar='FILE', help='correct output for failing test cases')
    parser.add_argument('--lines', metavar='LINE', nargs='*', help='suspicious lines')
    parser.add_argument('--build', metavar='CMD', default='make -e',
                        help='build command in the form of simple shell command (default: %(default)s)')
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
                        help='number of iterations through suspicious (default: %(default)s)')
    parser.add_argument('--localization', default='jaccard', choices=['jaccard', 'ochiai', 'tarantula'],
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

    logging.basicConfig(level=logging.INFO)

    working_dir = os.path.join(os.getcwd(), ".angelix")
    if os.path.exists(working_dir):
        shutil.rmtree(working_dir)
    os.mkdir(working_dir)

    with open(args.tests) as tests_file:
        tests = json.load(tests_file)

    if args.output is not None:
        with open(args.output) as output_file:
            output = json.load(output_file)
    else:
        output = None

    config = dict()
    config['initial_tests']       = args.initial_tests
    config['conditions_only']     = args.conditions_only
    config['test_timeout']        = args.test_timeout
    config['suspicious']          = args.suspicious
    config['iterations']          = args.iterations
    config['localization']        = args.localization
    config['klee_forks']          = args.klee_forks
    config['klee_timeout']        = args.klee_timeout
    config['klee_solver_timeout'] = args.klee_solver_timeout
    config['synthesis_timeout']   = args.synthesis_timeout
    config['synthesis_levels']    = args.synthesis_levels

    tool = Angelix(working_dir,
                   src = args.src,
                   buggy = args.buggy,
                   oracle = os.path.abspath(args.oracle),
                   tests = tests,
                   golden = args.golden,
                   output = output,
                   lines = args.lines,
                   build = args.build,
                   config = config)

    start = time.time()
    try:
        with time_limit(args.timeout):
            patch = tool.generate_patch()
    except TimeoutException:
        logger.info("failed to generate patch (timeout)")
        exit(1)
    end = time.time()
    elapsed = format_time(end - start)    
    
    if patch is None:
        logger.info("no patch is generated in {}".format(elapsed))
        exit(1)
    else:
        logger.info("patch is successfully generated in {} (see generated.diff)".format(elapsed))
        with open('generated.diff', 'w+') as file:
            for line in patch:
                file.write(line)
                print(line)
