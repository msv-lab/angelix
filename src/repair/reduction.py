import os
from subprocess import Popen
from utils import cd

class Reducer:

    def __init__(self, config, oracle):
        self.config = config

    def __call__(positive, negative, expressions, number):

        args = parser.parse_args()

        tests = args.test_suite.split()
        number = args.number
        number_failing = args.failing
        basepath = args.stat
        source_dirs = [sd for sd in listdir(basepath) if os.path.isdir(os.path.join(basepath, sd))]

        # test id -> source name -> set of locations
        data = {}

        passing_tests = set()
        failing_tests = set()

        # loading data from stat directory:
        for source in source_dirs:
            path = os.path.join(basepath, source)

            for test in tests:
                if not test in data:
                    data[test] = {}

                pass_file_path = os.path.join(path, "{}.pass.trace".format(test))
                fail_file_path = os.path.join(path, "{}.fail.trace".format(test))
                if os.path.isfile(pass_file_path):
                    trace_file = pass_file_path
                    passing_tests.add(test)
                elif os.path.isfile(fail_file_path):
                    trace_file = fail_file_path
                    failing_tests.add(test)
                else:
                    data[test][source] = set()
                    continue

                with open(trace_file) as f:
                    lines = set(f.readlines())
                    coverage = map(lambda line: line.rstrip(), lines)
                    data[test][source] = set(coverage)

        # selecting best tests:
        current_coverage = {}
        for source in source_dirs:
            current_coverage[source] = set()

        def select_best_tests(candidates, max_number):
            selected = set()
            for i in range(0, max_number):
                if len(candidates) == 0:
                    break
                best_increment = {}
                for source in source_dirs:
                    best_increment[source] = 0

                best_increment_total = 0
                best_test = random.sample(candidates, 1)[0]

                best_coverage = 0
                best_coverage_test = best_test

                for test in candidates:
                    current_increment = {}
                    current_increment_total = 0
                    coverage = 0
                    for source in source_dirs:
                        current_increment[source] = len(data[test][source] - current_coverage[source])
                        current_increment_total = current_increment_total + current_increment[source]
                        coverage = coverage + len(data[test][source])
                    if current_increment_total > best_increment_total:
                        best_increment = current_increment
                        best_increment_total = current_increment_total
                        best_test = test
                    if coverage > best_coverage:
                        best_coverage = coverage
                        best_coverage_test = test

                if best_increment_total > 0:
                    selected.add(best_test)
                    candidates.remove(best_test)
                    for source in source_dirs:
                        current_coverage[source] = current_coverage[source] | data[best_test][source]
                elif best_coverage > 0:
                    selected.add(best_coverage_test)
                    candidates.remove(best_coverage_test)
                    for source in source_dirs:
                        current_coverage[source] = data[best_coverage_test][source]
                else:
                    break

            return selected

        selected_failing = select_best_tests(failing_tests, number_failing)
        number_selected_failing = len(selected_failing)

        selected_passing = select_best_tests(passing_tests, number - number_selected_failing)
        number_selected_passing = len(selected_passing)

        total_selected = number_selected_passing + number_selected_failing

        # print output and log
        if os.environ.get('AF_DEBUG'):
            print("[af-reduce] selected passing tests: ", selected_passing, file=sys.stderr)
            print("[af-reduce] selected failing tests: ", selected_failing, file=sys.stderr)
            print("[af-reduce] number of selected tests: ", total_selected, file=sys.stderr)

        for test in selected_failing | selected_passing:
            print(test)

