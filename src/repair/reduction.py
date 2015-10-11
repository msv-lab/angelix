import logging


logger = logging.getLogger(__name__)


class Reducer:

    def __init__(self, config):
        self.config = config

    def __call__(self, positive, negative, expressions):
        '''
        positive, negative: (test * trace) list
        trace: expression list

        computes config['initial_tests'] tests that maximally cover given expressions
        '''
        number = self.config['initial_tests']
        number_failing = 1

        # this code was originally written for multiple files:
        source_name = ''
        source_dirs = [source_name]

        # test id -> source name -> set of locations
        data = {}

        passing_tests = []
        failing_tests = []

        # selecting best tests:
        relevant = set(expressions)

        for test, trace in positive:
            data[test] = dict()
            data[test][source_name] = set(trace) & relevant
            passing_tests.append(test)

        for test, trace in negative:
            data[test] = dict()
            data[test][source_name] = set(trace) & relevant
            failing_tests.append(test)

        current_coverage = {}
        for source in source_dirs:
            current_coverage[source] = set()

        def select_best_tests(candidates, max_number):
            selected = []
            for i in range(0, max_number):
                if len(candidates) == 0:
                    break
                best_increment = {}
                for source in source_dirs:
                    best_increment[source] = 0

                best_increment_total = 0

                best_test = candidates[0]

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
                    selected.append(best_test)
                    candidates.remove(best_test)
                    for source in source_dirs:
                        current_coverage[source] = current_coverage[source] | data[best_test][source]
                elif best_coverage > 0:
                    selected.append(best_coverage_test)
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

        logger.info("selected {} tests".format(total_selected))
        logger.info("selected passing tests: {}".format(selected_passing))
        logger.info("selected failing tests: {}".format(selected_failing))

        return selected_failing + selected_passing
