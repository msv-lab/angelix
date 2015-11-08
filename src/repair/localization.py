from math import sqrt
import logging


logger = logging.getLogger(__name__)


def ochiai(executed_passing, executed_failing, total_passing, total_failing):
    assert total_failing > 0
    if executed_failing + executed_passing == 0:
        return 0
    return executed_failing / sqrt(total_failing * (executed_passing + executed_failing))


def jaccard(executed_passing, executed_failing, total_passing, total_failing):
    assert total_failing > 0
    return executed_failing / (total_failing + executed_passing)


def tarantula(executed_passing, executed_failing, total_passing, total_failing):
    assert total_passing > 0 and total_failing > 0
    if executed_failing + executed_passing == 0:
        return 0
    return ((executed_failing / total_failing) /
            ((executed_failing / total_failing) + (executed_passing / total_passing)))


class Localizer:

    def __init__(self, config, lines):
        self.lines = lines
        self.config = config

    def __call__(self, positive, negative):
        '''
        positive, negative: (test * trace) list
        trace: expression list

        computes config['iterations'] groups
        each consisting of config['multiline'] suspicious expressions
        '''

        multiline = self.config['multiline']
        iterations = self.config['iterations']

        if self.config['localization'] == 'ochiai':
            formula = ochiai
        elif self.config['localization'] == 'jaccard':
            formula = jaccard
        elif self.config['localization'] == 'tarantula':
            formula = tarantula

        all = set()

        for _, trace in positive:
            all |= set(trace)

        for _, trace in negative:
            all |= set(trace)

        executed_positive = dict()
        executed_negative = dict()

        for e in all:
            executed_positive[e] = 0
            executed_negative[e] = 0

        for _, trace in positive:
            executed = set(trace)
            for e in executed:
                executed_positive[e] += 1

        for _, trace in negative:
            executed = set(trace)
            for e in executed:
                executed_negative[e] += 1

        with_score = []

        def is_selected(expr):
            return expr[0] in self.lines

        if self.lines is not None:
            filtered = filter(is_selected, all)
            all = filtered

        for e in all:
            score = formula(executed_positive[e], executed_negative[e], len(positive), len(negative))
            with_score.append((e, score))

        ranking = sorted(with_score, key=lambda r: r[1], reverse=True)
        top = ranking[:multiline * iterations]

        sorted_by_line = sorted(top, key=lambda r: r[0][0])

        groups = []
        for i in range(0, iterations):
            if len(sorted_by_line) == 0:
                break
            groups.append([])
            for j in range(0, multiline):
                if len(sorted_by_line) == 0:
                    break
                expr, score = sorted_by_line.pop()
                groups[i].append(expr)
                logger.info("selected expression {} with score {:.5} in group {}".format(expr, score, i))
        return groups
