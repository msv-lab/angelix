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

    def __call__(self, project, test, dump=None, trace=None, klee=False, env=os.environ):
        src = basename(project.dir)
        if klee:
            logger.info('running test \'{}\' of {} source with KLEE'.format(test, src))
        else:
            logger.info('running test \'{}\' of {} source'.format(test, src))
        environment = dict(env)

        if dump is not None:
            environment['ANGELIX_WITH_DUMPING'] = dump
        if trace is not None:
            environment['ANGELIX_WITH_TRACING'] = trace
        if (trace is not None) or (dump is not None):
            environment['ANGELIX_RUN'] = 'angelix-run-test'
        if klee:
            environment['ANGELIX_RUN'] = 'angelix-run-klee'
            
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(project.dir):
            proc = subprocess.Popen(self.oracle + " " + test, env=environment, stderr=stderr, shell=True)
            if klee:
                code = proc.wait()  # KLEE has its own timeout
            else:
                code = proc.wait(timeout=self.config['test_timeout'])
                
        return code == 0
