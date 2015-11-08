import os
import subprocess
from utils import cd
import logging
import json
from pprint import pprint
import tempfile
import shutil
from os.path import join


logger = logging.getLogger(__name__)


class Synthesizer:

    def __init__(self, config, extracted, angelic_forest_file):
        self.config = config
        self.extracted = extracted
        self.angelic_forest_file = angelic_forest_file

    def dump_angelic_forest(self, angelic_forest):
        '''
        Convert angelic forest to format more suitable for current synthesis engine
        '''
        def id(expr):
            return '{}-{}-{}-{}'.format(*expr)

        dumpable_angelic_forest = dict()
        for test, paths in angelic_forest.items():
            dumpable_paths = []
            for path in paths:
                dumpable_path = []

                for expr, values in path.items():
                    for instance, value in enumerate(values):
                        angelic, _, environment = value  # ignore original for now
                        context = []
                        for name, value in environment.items():
                            context.append({'name': name,
                                            'value': value})
                        dumpable_path.append({ 'context': context,
                                               'value': { 'name': 'angelic',
                                                          'value': angelic },
                                               'expression': id(expr),
                                               'instId': instance })
                dumpable_paths.append(dumpable_path)
            dumpable_angelic_forest[test] = dumpable_paths

        with open(self.angelic_forest_file, 'w') as file:
            json.dump(dumpable_angelic_forest, file, indent=2)

    def __call__(self, angelic_forest):

        self.dump_angelic_forest(angelic_forest)

        dirpath = tempfile.mkdtemp()
        patch_file = join(dirpath, 'patch')
        config_file = join(dirpath, 'config.json')

        for level in self.config['synthesis_levels']:

            logger.info('synthesizing patch with component level \'{}\''.format(level))

            # TODO: add synthesis-max-variables

            config = {
                "encodingConfig": {
                    "componentsMultipleOccurrences": False,
                    "phantomComponents": True,
                    "repairBooleanConst": False,
                    "repairIntegerConst": False,
                    "level": "linear"
                },
                "simplification": False,
                "spaceReduction": True,
                "componentLevel": level,
                "solverBound": 3,
                "solverTimeout": self.config['synthesis_timeout']
            }

            with open(config_file, 'w') as file:
                json.dump(config, file)

            jar = os.environ['SYNTHESIS_JAR']

            if self.config['verbose']:
                stderr = None
            else:
                stderr = subprocess.DEVNULL

            args = [self.angelic_forest_file, self.extracted, patch_file, config_file]

            try:
                result = subprocess.check_output(['java', '-jar', jar] + args, stderr=stderr)
            except subprocess.CalledProcessError:        
                logger.warning("synthesis returned non-zero code")
                continue

            if str(result, 'UTF-8').strip() == 'TIMEOUT':
                logger.warning('timeout when synthesizing fix')
            elif str(result, 'UTF-8').strip() == 'FAIL':
                logger.info('synthesis failed')
            elif str(result, 'UTF-8').strip() == 'SUCCESS':
                with open(patch_file) as file:
                    content = file.readlines()
                patch = dict()
                while len(content) > 0:
                    line = content.pop(0)
                    if len(line) == 0:
                        continue
                    expr = tuple(map(int, line.strip().split('-')))
                    original = content.pop(0).strip()
                    fixed = content.pop(0).strip()
                    logger.info('fixing expression {}: {} ---> {}'.format(expr, original, fixed))
                    patch[expr] = fixed
                return patch
            else:
                raise Exception('result: ' + str(result, 'UTF-8'))

        shutil.rmtree(dirpath)
        
        return None
