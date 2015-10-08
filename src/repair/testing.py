import os
from utils import cd
from subprocess import Popen
import logging


logger = logging.getLogger(__name__)


class Tester:

    def __init__(self, config, oracle):
        self.config = config
        self.oracle = oracle

    def __call__(self, project, test, dump=None, trace=None):
        src = os.path.basename(project.dir)
        logger.info('running test {} of {} source'.format(test, src))
        environment = dict(os.environ)
        environment['ANGELIX_TEST'] = test
        if dump is not None:
            environment['ANGELIX_DUMP'] = dump
        if trace is not None:
            environment['ANGELIX_TRACE'] = trace

        with cd(project.dir):
            proc = Popen([self.oracle, test], env=environment)
            code = proc.wait(timeout=self.config['test_timeout'])

        return code == 0                
            
                
