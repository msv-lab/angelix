import configparser
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

    def __init__(self, working_dir, src, buggy, oracle, tests,
                 golden=None, dump=None, lines=[], config=None):
        defaults = os.path.join(os.environ['ANGELIX_ROOT'], 'defaults.cfg')
        self.config = configparser.ConfigParser()
        self.config.read_file(open(defaults))
        if not(config is None):
            self.config.read(config)

        with open(tests) as tests_file:
            tests_spec = json.load(tests_file)
        self.test_suite = map(lambda t: t['id'], tests_spec)

        extracted = os.path.join(working_dir, 'extracted')
        os.mkdir(self.dir)

        self.run_test = Tester(self.config, oracle)
        self.groups_of_suspicious = Localizer(self.config, lines)
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
            initial_suite = self.reduce(positive, negative, expressions)
            self.backend_src.restore_buggy()
            self.instrument_for_inference(self.backend_src, expressions)
            angelic_forest = dict()
            for test in initial_suite:
                angelic_forest[test] = self.infer_specification(self.backend_src, test)
            initial_fix = self.synthesize_fix(angelic_forest)
            if initial_fix is None:
                continue
            self.validation_src.restore_buggy()
            self.apply_fix(self.validation_src, initial_fix)
            positive, negative = evaluate(self.validation_src)
            # here I need to be sure that negative tests are indeed regressions
            while len(negative) > 0:
                counterexample = negative.pop()
                angelic_forest[counterexample] = self.infer_specification(self.backend_src, counterexample)
                fix = self.synthesize_fix(angelic_forest)
                self.validation_src.restore_buggy()
                self.apply_fix(self.validation_src, fix)
                positive, negative = evaluate(self.validation_src)

        if len(negative) > 0:
            return None
        else:
            return self.validation_src.diff_buggy()


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument('src', help='source directory', required=True)
    parser.add_argument('buggy', help='relative path to buggy file', required=True)
    parser.add_argument('oracle', help='oracle script', required=True)
    parser.add_argument('tests', help='tests JSON database', required=True)
    parser.add_argument('--golden', metavar='DIR', help='golden source directory')
    parser.add_argument('--dump', metavar='DIR', help='correct dump for failing test cases')
    parser.add_argument('--lines', metavar='LINE', nargs='*', help='suspicious lines')
    parser.add_argument('--config', help='configuration file')
    
    args = parser.parse_args()

    working_dir = os.path.join(os.getcwd(), ".angelix")
    if os.path.exists(working_dir):
        shutil.rmtree(working_dir)
        os.makedirs(working_dir)

    tool = Angelix(working_dir = working_dir,
                   src = args.src,
                   buggy = args.buggy,
                   oracle = args.oracle,
                   tests = args.tests,
                   golden = args.golden,
                   dump = args.dump,
                   lines = args.lines,
                   config = args.config)

    patch = tool.generate_patch()
    
    if patch is None:
        print("Failed to generate patch in {}".format(elapsed))
    else:
        print("Patch is successfully generated in {} (see generated.diff)".format(elapsed))
        with open('generated.diff', 'w+') as file:
            for line in patch:
                file.write(line)
