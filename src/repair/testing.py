import os
from os.path import basename, join
from utils import cd
import subprocess
import logging
import sys


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
            reachable_dir = join(dump, 'reachable')  # maybe it should be done in other place?
            os.mkdir(reachable_dir)
        if trace is not None:
            environment['ANGELIX_WITH_TRACING'] = trace
        if (trace is not None) or (dump is not None):
            environment['ANGELIX_RUN'] = 'angelix-run-test'
        if klee:
            environment['ANGELIX_RUN'] = 'angelix-run-klee'
            # using stub library to make lli work
            environment['LLVMINTERP'] = 'lli -load {}/libkleeRuntest.so'.format(os.environ['KLEE_LIBRARY_PATH'])

        if self.config['verbose']:
            subproc_output = sys.stderr
        else:
            subproc_output = subprocess.DEVNULL

        with cd(project.dir):
            proc = subprocess.Popen(self.oracle + " " + test,
                                    env=environment,
                                    stdout=subproc_output,
                                    stderr=subproc_output,
                                    shell=True)
            if klee:
                code = proc.wait()  # KLEE has its own timeout
            else:
                code = proc.wait(timeout=self.config['test_timeout'])
                
        return code == 0
