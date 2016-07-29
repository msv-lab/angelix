import os, stat
from os.path import join, exists, abspath, basename
import shutil
import argparse
import time
import json
import logging
import time
import sys
import statistics

from project import Validation, Frontend, Backend, CompilationError
from utils import format_time, time_limit, TimeoutException
from runtime import Dump, Trace, Load
from transformation import RepairableTransformer, SuspiciousTransformer, \
                           FixInjector, TransformationError
from testing import Tester
from localization import Localizer
from reduction import Reducer
from inference import Inferrer, InferenceError, NoSmtError
from semfix_infer import Semfix_Inferrer
from synthesis import Synthesizer
from semfix_syn import Semfix_Synthesizer


logger = logging.getLogger("repair")


SYNTHESIS_LEVELS = ['alternatives',
                    'integer-constants',
                    'boolean-constants',
                    'variables',
                    'basic-arithmetic',
                    'basic-logic',
                    'basic-inequalities',
                    'extended-arithmetic',
                    'extended-logic',
                    'extended-inequalities',
                    'mixed-conditional',
                    'conditional-arithmetic']


DEFECT_CLASSES = ['if-conditions',
                  'assignments',
                  'loop-conditions',
                  'guards']


DEFAULT_DEFECTS = ['if-conditions', 'assignments']


KLEE_SEARCH_STRATEGIES = ['dfs', 'bfs', 'random-state', 'random-path',
                          'nurs:covnew', 'nurs:md2u', 'nurs:depth',
                          'nurs:icnt', 'nurs:cpicnt', 'nurs:qc']


DEFAULT_GROUP_SIZE = 2


DEFAULT_INITIAL_TESTS = 2


sys.setrecursionlimit(10000)  # Otherwise inference.get_vars fails


class Angelix:

    def __init__(self, working_dir, src, buggy, oracle, tests, golden, asserts, lines, build, configure, config):
        self.working_dir = working_dir
        self.config = config
        self.repair_test_suite = tests[:]
        self.validation_test_suite = tests[:]
        extracted = join(working_dir, 'extracted')
        os.mkdir(extracted)

        angelic_forest_file = join(working_dir, 'last-angelic-forest.json')

        tester = Tester(config, oracle, abspath(working_dir))
        self.run_test = tester
        self.get_suspicious_groups = Localizer(config, lines)
        self.reduce = Reducer(config)
        if self.config['use_semfix_syn']:
            self.synthesize_fix = Semfix_Synthesizer(working_dir,
                                                     config, extracted, angelic_forest_file)
            self.infer_spec = Semfix_Inferrer(working_dir, config, tester)
        else:
            self.synthesize_fix = Synthesizer(config, extracted, angelic_forest_file)
            self.infer_spec = Inferrer(config, tester, Load(working_dir))
        self.instrument_for_localization = RepairableTransformer(config)
        self.instrument_for_inference = SuspiciousTransformer(config, extracted)
        self.apply_patch = FixInjector(config)

        # check build only options
        if self.config['build_validation_only']:
            validation_dir = join(working_dir, "validation")
            shutil.copytree(src, validation_dir, symlinks=True)
            self.validation_src = Validation(config, validation_dir, buggy, build, configure)
            self.validation_src.configure()
            compilation_db = self.validation_src.export_compilation_db()
            self.validation_src.import_compilation_db(compilation_db)
            sys.exit()

        if self.config['build_golden_only']:
            golden_dir = join(working_dir, "golden")
            shutil.copytree(golden, golden_dir, symlinks=True)
            self.golden_src = Frontend(config, golden_dir, buggy, build, configure)
            self.golden_src.configure()
            self.golden_src.build()
            sys.exit()

        if self.config['build_backend_only']:
            backend_dir = join(working_dir, "backend")
            shutil.copytree(src, backend_dir, symlinks=True)
            self.backend_src = Backend(config, backend_dir, buggy, build, configure)
            self.backend_src.configure()
            self.backend_src.build()
            sys.exit()

        validation_dir = join(working_dir, "validation")
        shutil.copytree(src, validation_dir, symlinks=True)
        self.validation_src = Validation(config, validation_dir, buggy, build, configure)
        self.validation_src.configure()
        compilation_db = self.validation_src.export_compilation_db()
        self.validation_src.import_compilation_db(compilation_db)

        frontend_dir = join(working_dir, "frontend")
        shutil.copytree(src, frontend_dir, symlinks=True)
        self.frontend_src = Frontend(config, frontend_dir, buggy, build, configure)
        self.frontend_src.import_compilation_db(compilation_db)

        backend_dir = join(working_dir, "backend")
        shutil.copytree(src, backend_dir, symlinks=True)
        self.backend_src = Backend(config, backend_dir, buggy, build, configure)
        self.backend_src.import_compilation_db(compilation_db)

        if golden is not None:
            golden_dir = join(working_dir, "golden")
            shutil.copytree(golden, golden_dir, symlinks=True)
            self.golden_src = Frontend(config, golden_dir, buggy, build, configure)
            self.golden_src.import_compilation_db(compilation_db)
        else:
            self.golden_src = None

        self.dump = Dump(working_dir, asserts)
        self.trace = Trace(working_dir)


    def evaluate(self, src):
        testing_start_time = time.time()

        positive = []
        negative = []

        for test in self.validation_test_suite:
            if self.run_test(src, test):
                positive.append(test)
            else:
                negative.append(test)

        # make sure if failing tests really fail
        if self.config['redundant_test']:
            negative_copy = negative[:]
            for test in negative_copy:
                if self.run_test(src, test):
                    negative.remove(test)
                    positive.append(test)

        testing_end_time = time.time()
        testing_elapsed = testing_end_time - testing_start_time
        statistics.data['time']['testing'] += testing_elapsed
        statistics.save()

        return positive, negative


    def generate_patch(self):
        positive, negative = self.evaluate(self.validation_src)

        self.frontend_src.configure()
        if config['build_before_instr']:
            self.frontend_src.build()
        self.instrument_for_localization(self.frontend_src)
        self.frontend_src.build()

        testing_start_time = time.time()
        if len(positive) > 0:
            logger.info('running positive tests for debugging')
        for test in positive:
            self.trace += test
            if test not in self.dump:
                self.dump += test
                _, instrumented = self.run_test(self.frontend_src, test, dump=self.dump[test], trace=self.trace[test], check_instrumented=True)
                if not instrumented:
                    self.repair_test_suite.remove(test)
            else:
                _, instrumented = self.run_test(self.frontend_src, test, trace=self.trace[test], check_instrumented=True)
                if not instrumented:
                    self.repair_test_suite.remove(test)

        golden_is_built = False
        excluded = []

        if len(negative) > 0:
            logger.info('running negative tests for debugging')
        for test in negative:
            self.trace += test
            _, instrumented = self.run_test(self.frontend_src, test, trace=self.trace[test], check_instrumented=True)
            if not instrumented:
                self.repair_test_suite.remove(test)
            if test not in self.dump:
                if self.golden_src is None:
                    logger.error("golden version or assert file needed for test {}".format(test))
                    return None
                if not golden_is_built:
                    self.golden_src.configure()
                    self.golden_src.build()
                    golden_is_built = True
                self.dump += test
                result = self.run_test(self.golden_src, test, dump=self.dump[test])
                if not result:
                    excluded.append(test)

        for test in excluded:
            if not self.config['mute_test_message']:
                logger.warning('excluding test {} because it fails in golden version'.format(test))
            negative.remove(test)
            if test in self.repair_test_suite:
                self.repair_test_suite.remove(test)
            self.validation_test_suite.remove(test)

        testing_end_time = time.time()
        testing_elapsed = testing_end_time - testing_start_time
        statistics.data['time']['testing'] += testing_elapsed
        statistics.save()

        logger.info("repair test suite: {}".format(self.repair_test_suite))
        logger.info("validation test suite: {}".format(self.validation_test_suite))

        positive_traces = [(test, self.trace.parse(test)) for test in positive]
        negative_traces = [(test, self.trace.parse(test)) for test in negative]
        suspicious = self.get_suspicious_groups(self.validation_test_suite, positive_traces, negative_traces)

        if self.config['localize_only']:
            for idx, (group, score) in enumerate(suspicious):
                logger.info('group {}: {} ({})'.format(idx+1, group, score))
            exit(0)

        if len(suspicious) == 0:
            logger.warning('no suspicious expressions localized')

        repaired = len(negative) == 0

        while not repaired and len(suspicious) > 0:
            if self.config['use_semfix_syn']:
                # prepare a clean directory
                shutil.rmtree(join(self.working_dir, 'semfix-syn-input'),
                              ignore_errors='true')

            expressions = suspicious.pop(0)
            logger.info('considering suspicious expressions {}'.format(expressions))
            current_repair_suite = self.reduce(self.repair_test_suite, positive_traces, negative_traces, expressions)

            self.backend_src.restore_buggy()
            self.backend_src.configure()
            if config['build_before_instr']:
                self.backend_src.build()
            self.instrument_for_inference(self.backend_src, expressions)
            self.backend_src.build()

            angelic_forest = dict()
            inference_failed = False
            for test in current_repair_suite:
                try:
                    angelic_forest[test] = self.infer_spec(self.backend_src, test, self.dump[test], self.frontend_src)
                    if len(angelic_forest[test]) == 0:
                        if test in positive:
                            logger.warning('angelic forest for positive test {} not found'.format(test))
                            current_repair_suite.remove(test)
                            del angelic_forest[test]
                            continue
                        inference_failed = True
                        break
                except InferenceError:
                    logger.warning('inference failed (error was raised)')
                    inference_failed = True
                    break
                except NoSmtError:
                    if test in positive:
                        current_repair_suite.remove(test)
                        continue
                    inference_failed = True
                    break
            if inference_failed:
                continue
            initial_fix = self.synthesize_fix(angelic_forest)
            if initial_fix is None:
                logger.info('cannot synthesize fix')
                continue
            logger.info('candidate fix synthesized')

            self.validation_src.restore_buggy()
            try:
                self.apply_patch(self.validation_src, initial_fix)
            except TransformationError:
                logger.info('cannot apply fix')
                continue
            self.validation_src.build()

            pos, neg = self.evaluate(self.validation_src)
            if not set(neg).isdisjoint(set(current_repair_suite)):
                not_repaired = list(set(current_repair_suite) & set(neg))
                logger.warning("generated invalid fix (tests {} not repaired)".format(not_repaired))
                continue
            repaired = len(neg) == 0
            neg = list(set(neg) & set(self.repair_test_suite))
            current_positive, current_negative = pos, neg

            if len(current_negative) == 0 and not repaired:
                logger.warning("cannot repair using instrumented tests")
                continue

            negative_idx = 0
            while not repaired:
                counterexample = current_negative[negative_idx]

                logger.info('counterexample test is {}'.format(counterexample))
                current_repair_suite.append(counterexample)
                try:
                    angelic_forest[counterexample] = self.infer_spec(self.backend_src,
                                                                     counterexample,
                                                                     self.dump[counterexample],
                                                                     self.frontend_src)
                except NoSmtError:
                    logger.warning("no smt file for test {}".format(counterexample))
                    negative_idx = negative_idx + 1
                    if len(current_negative) - negative_idx > 0:
                        continue
                    break
                if len(angelic_forest[counterexample]) == 0:
                    break
                fix = self.synthesize_fix(angelic_forest)
                if fix is None:
                    logger.info('cannot refine fix')
                    break
                logger.info('refined fix is synthesized')
                self.validation_src.restore_buggy()
                self.apply_patch(self.validation_src, fix)
                self.validation_src.build()
                pos, neg = self.evaluate(self.validation_src)
                repaired = len(neg) == 0
                neg = list(set(neg) & set(self.repair_test_suite))
                current_positive, current_negative = pos, neg

                if not set(current_negative).isdisjoint(set(current_repair_suite)):
                    not_repaired = list(set(current_repair_suite) & set(current_negative))
                    logger.warning("generated invalid fix (tests {} not repaired)".format(not_repaired))
                    break
                negative_idx = 0

        if not repaired:
            return None
        else:
            return self.validation_src.diff_buggy()

    def dump_outputs(self):
        self.frontend_src.configure()
        if config['build_before_instr']:
            self.frontend_src.build()
        self.instrument_for_localization(self.frontend_src)
        self.frontend_src.build()
        logger.info('running tests for dumping')
        for test in self.validation_test_suite:
            self.dump += test
            result = self.run_test(self.frontend_src, test, dump=self.dump[test])
            if result:
                logger.info('test passed')
            else:
                logger.info('test failed')
        return self.dump.export()

    def synthesize_from(self, af_file):
        with open(af_file) as file:
            data = json.load(file)
        repair_suite = data.keys()

        expressions = set()
        for _, paths in data.items():
           for path in paths:
               for value in path:
                   expr = tuple(map(int, value['expression'].split('-')))
                   expressions.add(expr)

        # we need this to extract buggy expressions:
        self.backend_src.restore_buggy()
        self.backend_src.configure()
        if config['build_before_instr']:
            self.backend_src.build()
        self.instrument_for_inference(self.backend_src, list(expressions))

        fix = self.synthesize_fix(af_file)
        if fix is None:
            logger.info('cannot synthesize fix')
            return None
        logger.info('fix is synthesized')

        self.validation_src.restore_buggy()
        self.apply_patch(self.validation_src, fix)
        self.validation_src.build()
        positive, negative = self.evaluate(self.validation_src)
        if not set(negative).isdisjoint(set(repair_suite)):
            not_repaired = list(set(repair_suite) & set(negative))
            logger.warning("generated invalid fix (tests {} not repaired)".format(not_repaired))
            return None

        if len(negative) > 0:
            logger.info("tests {} fail".format(negative))
            return None
        else:
            return self.validation_src.diff_buggy()


if __name__ == "__main__":

    parser = argparse.ArgumentParser('angelix')
    parser.add_argument('src', metavar='SOURCE', help='source directory')
    parser.add_argument('buggy', metavar='BUGGY', help='relative path to buggy file')
    parser.add_argument('oracle', metavar='ORACLE', help='oracle script')
    parser.add_argument('tests', metavar='TEST', nargs='+', help='test case')
    parser.add_argument('--golden', metavar='DIR', help='golden source directory')
    parser.add_argument('--assert', metavar='FILE', help='assert expected outputs')
    parser.add_argument('--defect', metavar='CLASS', nargs='+',
                        default=DEFAULT_DEFECTS,
                        choices=DEFECT_CLASSES,
                        help='defect classes (default: %(default)s). choices: ' + ', '.join(DEFECT_CLASSES))
    parser.add_argument('--lines', metavar='LINE', type=int, nargs='+', help='suspicious lines (default: all)')
    parser.add_argument('--configure', metavar='CMD', default=None,
                        help='configure command in the form of shell command (default: %(default)s)')
    parser.add_argument('--build', metavar='CMD', default='make -e',
                        help='build command in the form of simple shell command (default: %(default)s)')
    parser.add_argument('--build-before-instr', action='store_true',
                        help='build source before (and after) instrumentation (default: %(default)s)')
    parser.add_argument('--timeout', metavar='SEC', type=int, default=None,
                        help='total repair timeout (default: %(default)s)')
    parser.add_argument('--initial-tests', metavar='NUM', type=int, default=DEFAULT_INITIAL_TESTS,
                        help='initial repair test suite size (default: %(default)s)')
    parser.add_argument('--all-tests', action='store_true',
                        help='use all tests for repair (default: %(default)s)')
    parser.add_argument('--test-timeout', metavar='MS', type=int, default=None,
                        help='test case timeout (default: %(default)s)')
    parser.add_argument('--group-size', metavar='NUM', type=int, default=DEFAULT_GROUP_SIZE,
                        help='number of statements considered at once (default: %(default)s)')
    parser.add_argument('--group-by-score', action='store_true',
                        help='group statements by suspiciousness score (default: grouping by location)')
    parser.add_argument('--localize-from-bottom', action='store_true',
                        help='iterate suspicious expression from the bottom of file (default: localizing from top)')
    parser.add_argument('--suspicious', metavar='NUM', type=int, default=20,
                        help='total number of suspicious statements (default: %(default)s)')
    parser.add_argument('--localization', default='jaccard', choices=['jaccard', 'ochiai', 'tarantula'],
                        help='formula for localization algorithm (default: %(default)s)')
    parser.add_argument('--ignore-trivial', action='store_true',
                        help='ignore trivial expressions: variables and constants (default: %(default)s)')
    parser.add_argument('--path-solving-timeout', metavar='MS', type=int, default=60000, # 60 seconds
                        help='timeout for extracting single angelic path (default: %(default)s)')
    parser.add_argument('--max-angelic-paths', metavar='NUM', type=int, default=None,
                        help='max number of angelic paths for a test case (default: %(default)s)')
    parser.add_argument('--klee-search', metavar='HEURISTIC', default=None,
                        choices=KLEE_SEARCH_STRATEGIES,
                        help='KLEE search heuristic (default: KLEE\'s default). choices: ' + ', '.join(KLEE_SEARCH_STRATEGIES))
    parser.add_argument('--klee-max-forks', metavar='NUM', type=int, default=None,
                        help='KLEE max number of forks (default: %(default)s)')
    parser.add_argument('--klee-max-depth', metavar='NUM', type=int, default=None,
                        help='KLEE max symbolic branches (default: %(default)s)')
    parser.add_argument('--klee-timeout', metavar='SEC', type=int, default=None,
                        help='KLEE timeout (default: %(default)s)')
    parser.add_argument('--klee-solver-timeout', metavar='SEC', type=int, default=None,
                        help='KLEE solver timeout (default: %(default)s)')
    parser.add_argument('--klee-debug', action='store_true',
                        help='print instructions executed by KLEE (default: %(default)s)')
    parser.add_argument('--klee-ignore-errors', action='store_true',
                        help='Don\'t terminate on memory errors (default: %(default)s)')
    parser.add_argument('--ignore-trans-errors', action='store_true',
                        help='Don\'t terminate on transformation errors (default: %(default)s)')
    parser.add_argument('--ignore-infer-errors', action='store_true',
                        help='Consider path with errors for inference (default: %(default)s)')
    parser.add_argument('--use-nsynth', action='store_true',
                        help='use new synthesizer (default: %(default)s)')
    parser.add_argument('--synthesis-timeout', metavar='MS', type=int, default=30000, # 30 sec
                        help='synthesis timeout (default: %(default)s)')
    parser.add_argument('--synthesis-levels', metavar='LEVEL', nargs='+',
                        choices=SYNTHESIS_LEVELS,
                        default=['alternatives', 'integer-constants', 'boolean-constants'],
                        help='component levels (default: %(default)s). choices: ' + ', '.join(SYNTHESIS_LEVELS))
    parser.add_argument('--synthesis-global-vars', action='store_true',
                        help='use global program variables for synthesis (default: %(default)s)')
    parser.add_argument('--synthesis-func-params', action='store_true',
                        help='use function parameters as variables for synthesis (default: %(default)s)')
    parser.add_argument('--synthesis-used-vars', action='store_true',
                        help='[deprecated] use variables that are used in scope for synthesis (default: %(default)s)')
    parser.add_argument('--synthesis-ptr-vars', action='store_true',
                        help='use pointer variables for synthesis (default: %(default)s)')
    parser.add_argument('--synthesis-bool-only', action='store_true',
                        help='synthesize only boolean expressions (default: %(default)s)')
    parser.add_argument('--semfix', action='store_true',
                        help='enable SemFix mode (default: %(default)s)')
    parser.add_argument('--use-semfix-synthesizer', action='store_true',
                        help='[deprecated] use SemFix synthesizer (default: %(default)s)')
    parser.add_argument('--max-z3-trials', metavar='NUM', type=int, default=2,
                        help='maxium Z3 trials when using SemFix synthesizer (default: %(default)s)')
    parser.add_argument('--dump-only', action='store_true',
                        help='dump actual outputs for given tests (default: %(default)s)')
    parser.add_argument('--synthesis-only', metavar="FILE", default=None,
                        help='synthesize and validate patch from angelic forest (default: %(default)s)')
    parser.add_argument('--redundant-test', action='store_true',
                        help='[deprecated] run tests redundantly (default: %(default)s)')
    parser.add_argument('--invalid-localization', action='store_true',
                        help='[deprecated] use tests that fail in golden version for localization (default: %(default)s)')
    parser.add_argument('--verbose', action='store_true',
                        help='print compilation and KLEE messages (default: %(default)s)')
    parser.add_argument('--quiet', action='store_true',
                        help='print only errors (default: %(default)s)')
    parser.add_argument('--mute-build-message', action='store_true',
                        help='mute build message (default: %(default)s)')
    parser.add_argument('--mute-test-message', action='store_true',
                        help='mute test message (default: %(default)s)')
    parser.add_argument('--mute-warning', action='store_true',
                        help='mute warning message (default: %(default)s)')
    parser.add_argument('--build-validation-only', action='store_true',
                        help='build validation source and terminate (default: %(default)s)')
    parser.add_argument('--build-golden-only', action='store_true',
                        help='build golden source and terminate (default: %(default)s)')
    parser.add_argument('--build-backend-only', action='store_true',
                        help='build backend source and terminate (default: %(default)s)')
    parser.add_argument('--ignore-lines', action='store_true',
                        help='[deprecated] ignore --lines options (default: %(default)s)')
    parser.add_argument('--localize-only', action='store_true',
                        help='show all suspicious expressions and terminate (default: %(default)s)')
    parser.add_argument('--term-when-syn-crashes', action='store_true',
                        help='[deprecated] terminate when synthesis crashes (default: %(default)s)'
                        if "AF_DEBUG" in os.environ
                        else argparse.SUPPRESS)
    parser.add_argument('--version', action='version', version='Angelix 1.0')

    args = parser.parse_args()

    def rm_force(action, name, exc):
        os.chmod(name, stat.S_IREAD)
        shutil.rmtree(name)

    working_dir = join(os.getcwd(), ".angelix")
    if exists(working_dir):
        shutil.rmtree(working_dir, onerror=rm_force)
    os.mkdir(working_dir)

    rootLogger = logging.getLogger()
    FORMAT = logging.Formatter('%(levelname)-8s %(name)-15s %(message)s')
    if args.quiet:
        rootLogger.setLevel(logging.WARNING)
    else:
        rootLogger.setLevel(logging.INFO)
    fileHandler = logging.FileHandler("{0}/{1}.log".format(working_dir, 'angelix'))
    fileHandler.setFormatter(FORMAT)
    rootLogger.addHandler(fileHandler)
    consoleHandler = logging.StreamHandler()
    consoleHandler.setFormatter(FORMAT)
    rootLogger.addHandler(consoleHandler)

    if vars(args)['assert'] is not None and not args.dump_only:
        with open(vars(args)['assert']) as output_file:
            asserts = json.load(output_file)
    else:
        asserts = None

    if 'guards' in args.defect and 'assignments' in args.defect:
        logger.error('\'guards\' and \'assignments\' defect classes are currently incompatible')
        exit(1)

    if args.semfix:
        args.use_semfix_synthesizer = True

    if args.use_semfix_synthesizer:
        args.semfix = True

    if args.semfix:
        # this should be tested first:
        # args.use_semfix_synthesizer = True
        if not (args.defect == DEFAULT_DEFECTS):
            logger.warning('--semfix disables --defect option')
        if args.ignore_trivial:
            logger.warning('--semfix disables --ignore-trivial option')
        if not (args.group_size == DEFAULT_GROUP_SIZE):
            logger.warning('--semfix disables --group-size option')
        args.group_size = 1

    if args.dump_only:
        if args.golden is not None:
            logger.warning('--dump-only disables --golden option')
        if asserts is not None:
            logger.warning('--dump-only disables --assert option')

    config = dict()
    config['initial_tests']         = args.initial_tests
    config['all_tests']             = args.all_tests
    config['semfix']                = args.semfix
    config['use_semfix_syn']        = args.use_semfix_synthesizer
    config['max_z3_trials']         = args.max_z3_trials
    config['defect']                = args.defect
    config['test_timeout']          = args.test_timeout
    config['group_size']            = args.group_size
    config['group_by_score']        = args.group_by_score
    config['localize_from_bottom']  = args.localize_from_bottom
    config['suspicious']            = args.suspicious
    config['localization']          = args.localization
    config['ignore_trivial']        = args.ignore_trivial
    config['path_solving_timeout']  = args.path_solving_timeout
    config['max_angelic_paths']     = args.max_angelic_paths
    config['klee_max_forks']        = args.klee_max_forks
    config['klee_max_depth']        = args.klee_max_depth
    config['klee_search']           = args.klee_search
    config['klee_timeout']          = args.klee_timeout
    config['klee_solver_timeout']   = args.klee_solver_timeout
    config['klee_debug']            = args.klee_debug
    config['klee_ignore_errors']    = args.klee_ignore_errors
    config['ignore_trans_errors']   = args.ignore_trans_errors
    config['ignore_infer_errors']   = args.ignore_infer_errors
    config['use_nsynth']            = args.use_nsynth
    config['synthesis_timeout']     = args.synthesis_timeout
    config['synthesis_levels']      = args.synthesis_levels
    config['synthesis_global_vars'] = args.synthesis_global_vars
    config['synthesis_func_params'] = args.synthesis_func_params
    config['synthesis_used_vars']   = args.synthesis_used_vars
    config['synthesis_ptr_vars']    = args.synthesis_ptr_vars
    config['synthesis_bool_only']   = args.synthesis_bool_only
    config['redundant_test']        = args.redundant_test
    config['verbose']               = args.verbose
    config['build_before_instr']    = args.build_before_instr
    config['mute_build_message']    = args.mute_build_message
    config['mute_test_message']     = args.mute_test_message
    config['mute_warning']          = args.mute_warning
    config['build_validation_only'] = args.build_validation_only
    config['build_golden_only']     = args.build_golden_only
    config['build_backend_only']    = args.build_backend_only
    config['localize_only']         = args.localize_only
    config['invalid_localization']  = args.invalid_localization
    config['term_when_syn_crashes'] = args.term_when_syn_crashes

    if args.verbose:
        for key, value in config.items():
            logger.info('option {} = {}'.format(key, value))

    statistics.init(working_dir)

    if args.ignore_lines:
        args.lines = None

    tool = Angelix(working_dir,
                   src=args.src,
                   buggy=args.buggy,
                   oracle=abspath(args.oracle),
                   tests=args.tests,
                   golden=args.golden,
                   asserts=asserts,
                   lines=args.lines,
                   build=args.build,
                   configure=args.configure,
                   config=config)

    if args.dump_only:
        try:
            dump = tool.dump_outputs()
            with open('dump.json', 'w') as output_file:
                asserts = json.dump(dump, output_file, indent=2)
            logger.info('outputs successfully dumped (see dump.json)')
            exit(0)
        except (CompilationError, TransformationError):
            logger.info('failed to dump outputs')
            exit(1)

    start = time.time()

    def repair():
        if args.synthesis_only is not None:
            return tool.synthesize_from(args.synthesis_only)
        else:
            return tool.generate_patch()

    try:
        if args.timeout is not None:
            with time_limit(args.timeout):
                patch = repair()
        else:
            patch = repair()
    except TimeoutException:
        logger.info("failed to generate patch (timeout)")
        print('TIMEOUT')
        exit(0)
    except (CompilationError, InferenceError, TransformationError):
        logger.info("failed to generate patch")
        print('FAIL')
        exit(1)

    end = time.time()
    elapsed = format_time(end - start)
    statistics.data['time']['total'] = end - start
    statistics.save()

    if patch is None:
        logger.info("no patch generated in {}".format(elapsed))
        print('FAIL')
        exit(0)
    else:
        patch_file = basename(abspath(args.src)) + '-' + time.strftime("%Y-%b%d-%H%M%S") + '.patch'
        logger.info("patch successfully generated in {} (see {})".format(elapsed, patch_file))
        print('SUCCESS')
        with open(patch_file, 'w+') as file:
            for line in patch:
                file.write(line)
        exit(0)
