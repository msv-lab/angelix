import sys
from math import sqrt, ceil
import logging


logger = logging.getLogger(__name__)

class NoNegativeTestException(Exception):
    pass

def ochiai(executed_passing, executed_failing, total_passing, total_failing):
    if not total_failing > 0:
        raise NoNegativeTestException()
    if executed_failing + executed_passing == 0:
        return 0
    return executed_failing / sqrt(total_failing * (executed_passing + executed_failing))


def jaccard(executed_passing, executed_failing, total_passing, total_failing):
    if not total_failing > 0:
        raise NoNegativeTestException()
    return executed_failing / (total_failing + executed_passing)


def tarantula(executed_passing, executed_failing, total_passing, total_failing):
    if not total_failing > 0:
        raise NoNegativeTestException()
    if executed_failing + executed_passing == 0:
        return 0
    return ((executed_failing / total_failing) /
            ((executed_failing / total_failing) + (executed_passing / total_passing)))


class Localizer:

    def __init__(self, config, lines):
        self.lines = lines
        self.config = config

    def __call__(self, test_suite, all_positive, all_negative):
        '''
        test_suite: tests under consideration
        all_positive, all_negative: (test * trace) list
        trace: expression list

        computes config['suspicious']/config['group_size'] groups
        each consisting of config['group_size'] suspicious expressions
        '''

        group_size = self.config['group_size']
        suspicious = self.config['suspicious']

        if self.config['localization'] == 'ochiai':
            formula = ochiai
        elif self.config['localization'] == 'jaccard':
            formula = jaccard
        elif self.config['localization'] == 'tarantula':
            formula = tarantula

        # first, remove irrelevant information:
        positive = []
        negative = []

        if not self.config['invalid_localization']:
            for test, trace in all_positive:
                if test in test_suite:
                    positive.append((test, trace))

            for test, trace in all_negative:
                if test in test_suite:
                    negative.append((test, trace))
        else:
            positive = all_positive
            negative = all_negative


        all = set()

        for _, trace in positive:
            all |= set(trace)

        for _, trace in negative:
            all |= set(trace)

        # update suspcious
        if self.config['localize_only']:
            suspicious = len(all)
            logger.info('trace size: {}'.format(suspicious))

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
            all = list(filtered)

        for e in all:
            try:
                score = formula(executed_positive[e], executed_negative[e], 
                                len(positive), len(negative))
                if not (score == 0.0):  # 0.0 mean not executed by failing test
                    with_score.append((e, score))
            except NoNegativeTestException:
                logger.info("No negative test exists")
                exit(0)

        ranking = sorted(with_score, key=lambda r: r[1], reverse=True)

        if self.config['group_by_score']:
            top = ranking[:suspicious]
        else:
            if self.config['localize_from_bottom']:
                top = sorted(ranking[:suspicious], key=lambda r: r[0][0], reverse=True)  # sort by location backward
            else:
                top = sorted(ranking[:suspicious], key=lambda r: r[0][0])  # sort by location

        groups_with_score = []
        for i in range(0, ceil(suspicious / group_size)):
            if len(top) == 0:
                break
            group = []
            total_score = 0
            for j in range(0, group_size):
                if len(top) == 0:
                    break
                expr, score = top.pop(0)
                total_score += score
                group.append(expr)
            groups_with_score.append((group, total_score))

        sorted_groups = sorted(groups_with_score, key=lambda r: r[1], reverse=True)

        groups = []
        for (group, score) in sorted_groups:
            groups.append(group)
            logger.info("selected expressions {} with group score {:.5} ".format(group, score))

        return groups
