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


def trantula(executed_passing, executed_failing, total_passing, total_failing):
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
        each consisting of config['suspicious'] suspicious
        '''

        suspicious = self.config['suspicious']
        iterations = self.config['iterations']

        if self.config['localization'] == 'ochiai':
            formula = ochiai
        elif self.config['localization'] == 'jaccard':
            formula = jaccard
        elif self.config['localization'] == 'tarantula':
            formula = tarantula

        all = set()
        executed_positive = dict()
        executed_negative = dict()

        for _, trace in positive:
            executed = set(trace)
            all |= executed
            for e in executed:
                if e in executed_positive:
                    executed_positive[e] += 1
                else:
                    executed_positive[e] = 1


        for _, trace in negative:
            executed = set(trace)
            all |= executed
            for e in executed:
                if e in executed_negative:
                    executed_negative[e] += 1
                else:
                    executed_negative[e] = 1

        ranking = []
        
        for e in all:
            score = formula(executed_positive[e], executed_negative[e], len(positive), len(negative))
            ranking.append((e, score))
        
        sorted(ranking, key=lambda r: r[1], reverse=True)
        top = ranking[:suspicious * iterations]

        sorted(top, key=lambda r: r[0][0])  # by beginning line

        groups = []
        for i in range(0, iterations):
            if len(top) == 0:
                break
            groups.append([])
            for j in range(0, suspicious):
                if len(top) == 0:
                    break
                expr, score = top.pop()
                groups[i].append(expr)
                logger.info("selected expression {} with score {:.5} in group {}".format(expr, score, i))
        return groups
