import os
from os.path import basename
from utils import cd
import subprocess
import logging


logger = logging.getLogger(__name__)


class Tester:

    def __init__(self, config, oracle):
        self.config = config
        self.oracle = oracle

    def __call__(self, project, test, dump=None, trace=None):
        src = basename(project.dir)
        logger.info('running test {} of {} source'.format(test, src))
        environment = dict(os.environ)
        if dump is not None:
            environment['ANGELIX_DUMP'] = dump
        if trace is not None:
            environment['ANGELIX_TRACE'] = trace

        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(project.dir):
            proc = subprocess.Popen([self.oracle, test], env=environment, stderr=stderr)
            code = proc.wait(timeout=self.config['test_timeout'])

        return code == 0
