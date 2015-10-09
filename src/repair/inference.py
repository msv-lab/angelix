import os
from os.path import join, dirname, relpath, basename
from utils import cd
import subprocess
import logging
from glob import glob
from os import listdir


logger = logging.getLogger(__name__)


class InferenceError(Exception):
    pass


class Inferrer:

    def __init__(self, config, tests_spec):
        self.config = config
        self.tests_spec = tests_spec

    def __call__(self, project, test, dump):
        logger.info('executing KLEE on test {}'.format(test))
        
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        exe = self.tests_spec[test]['executable'] + '.patched.bc'
        args = self.tests_spec[test]['arguments']
        klee_config = ['-write-smt2s',
                       '-smtlib-human-readable',
                       '--libc=uclibc',
                       '--posix-runtime',
                       '-max-forks={}'.format(self.config['klee_forks']),
                       '-max-time={}'.format(self.config['klee_timeout']),
                       '-max-solver-time={}'.format(self.config['klee_solver_timeout']),
                       '-allow-external-sym-calls']

        try:
            with cd(project.dir):
                subprocess.check_output(['klee'] + klee_config + [exe] + args, stderr=stderr)
        except subprocess.CalledProcessError:        
            logger.warning("KLEE returned non-zero code")

        smt_glob = join(dirname(join(project.dir, exe)), 'klee-last', '*.smt2')
        smt_files = glob(smt_glob)

        # loading dump:
        oracle = dict()

        vars = listdir(dump)
        for var in vars:
            instances = listdir(join(dump, var))
            if len(instances) != len(set(instances)):
                logger.error('corrupted dump for test {}'.format(test))
                raise InferenceError()
            oracle[var] = []
            for i in range(0, len(instances)):
                file = join(dump, var, str(i))
                with open(file) as f:
                    content = f.read()
                oracle[var].append(content)

        # solving path constraints:
        angelic_paths = []

        for smt in smt_files:
            logger.info('solving path {}'.format(relpath(smt)))

        return angelic_paths
        
