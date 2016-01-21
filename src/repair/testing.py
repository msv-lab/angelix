import os
from os.path import basename, join, exists
from utils import cd
import subprocess
import logging
import sys
import tempfile
from glob import glob

logger = logging.getLogger(__name__)

class KleeError(Exception):
    pass

class Tester:

    def __init__(self, config, oracle, workdir):
        self.config = config
        self.oracle = oracle
        self.workdir = workdir

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
        environment['ANGELIX_WORKDIR'] = self.workdir
        environment['ANGELIX_TEST_ID'] = test

        dirpath = tempfile.mkdtemp()
        executions = join(dirpath, 'executions')
        
        environment['ANGELIX_RUN_EXECUTIONS'] = executions

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
            if klee or self.config['test_timeout'] is None: # KLEE has its own timeout
                code = proc.wait()  
            else:
                code = proc.wait(timeout=self.config['test_timeout'])

        if dump is not None or trace is not None or klee:
            if exists(executions):
                with open(executions) as file:
                    content = file.read()
                    if len(content) > 1:
                        logger.warning("ANGELIX_RUN is executed multiple times by test {}".format(test))
            else:
                logger.warning("ANGELIX_RUN is not executed by test {}".format(test))

        if klee:
            backend_dir = join(self.workdir, "backend")
            klee_log_file = join(backend_dir, 'klee.log')
            with open(klee_log_file) as file:
                klee_error = False
                for line in file:
                    if line.endswith("longjmp unsupported\n"):
                        logger.warning('KLEE abrutply stopped because longjmp is not supported')
                        klee_error = True
            if klee_error:
                smt_glob = join(backend_dir, 'klee-out-0', '*.smt2')
                smt_files = glob(smt_glob)
                for smt in smt_files:
                    os.remove(smt)
                raise KleeError()
                
        return code == 0
