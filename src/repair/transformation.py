import os
import sys
from os.path import join, basename, relpath
import tempfile
import subprocess
from utils import cd
import logging
import shutil


logger = logging.getLogger(__name__)


class TransformationError(Exception):
    pass


class RepairableTransformer:

    def __init__(self, config):
        self.config = config
        if self.config['verbose']:
            self.subproc_output = sys.stderr
        else:
            self.subproc_output = subprocess.DEVNULL

    def __call__(self, project):
        src = basename(project.dir)
        logger.info('instrumenting repairable of {} source'.format(src))
        environment = dict(os.environ)
        if 'if-conditions' in self.config['defect']:
            environment['ANGELIX_IF_CONDITIONS_DEFECT_CLASS'] = 'YES'
        if 'assignments' in self.config['defect']:
            environment['ANGELIX_ASSIGNMENTS_DEFECT_CLASS'] = 'YES'
        if 'loop-conditions' in self.config['defect']:
            environment['ANGELIX_LOOP_CONDITIONS_DEFECT_CLASS'] = 'YES'
        if 'deletions' in self.config['defect']:
            environment['ANGELIX_DELETIONS_DEFECT_CLASS'] = 'YES'
        if 'guards' in self.config['defect']:
            environment['ANGELIX_GUARDS_DEFECT_CLASS'] = 'YES'
        if self.config['ignore_trivial']:
            environment['ANGELIX_IGNORE_TRIVIAL'] = 'YES'
        if self.config['semfix']:
            environment['ANGELIX_SEMFIX_MODE'] = 'YES'
        if self.config['use_semfix_syn']:
            environment['ANGELIX_USE_SEMFIX_SYN'] = 'YES'
        with cd(project.dir):
            return_code = subprocess.call(['instrument-repairable', project.buggy],
                                          stderr=self.subproc_output,
                                          stdout=self.subproc_output,
                                          env=environment)
        if return_code != 0:
            logger.error("transformation of {} failed".format(relpath(project.dir)))
            raise TransformationError()


class SuspiciousTransformer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted
        if self.config['verbose']:
            self.subproc_output = sys.stderr
        else:
            self.subproc_output = subprocess.DEVNULL

    def __call__(self, project, expressions):
        src = basename(project.dir)
        logger.info('instrumenting suspicious of {} source'.format(src))
        environment = dict(os.environ)
        dirpath = tempfile.mkdtemp()
        suspicious_file = join(dirpath, 'suspicious')
        with open(suspicious_file, 'w') as file:
            for e in expressions:
                file.write('{} {} {} {}\n'.format(*e))

        if self.config['semfix']:
            environment['ANGELIX_SEMFIX_MODE'] = 'YES'

        if self.config['synthesis_global_vars']:
            environment['ANGELIX_GLOBAL_VARIABLES'] = 'YES'

        if self.config['synthesis_func_params']:
            environment['ANGELIX_FUNCTION_PARAMETERS'] = 'YES'

        if self.config['synthesis_used_vars']:
            environment['ANGELIX_USED_VARIABLES'] = 'YES'

        environment['ANGELIX_EXTRACTED'] = self.extracted
        environment['ANGELIX_SUSPICIOUS'] = suspicious_file

        with cd(project.dir):
            return_code = subprocess.call(['instrument-suspicious', project.buggy],
                                          stderr=self.subproc_output,
                                          stdout=self.subproc_output,
                                          env=environment)
        if return_code != 0:
            logger.error("transformation of {} failed".format(relpath(project.dir)))
            raise TransformationError()

        shutil.rmtree(dirpath)


class FixInjector:

    def __init__(self, config):
        self.config = config
        if self.config['verbose']:
            self.subproc_output = sys.stderr
        else:
            self.subproc_output = subprocess.DEVNULL

    def __call__(self, project, patch):
        src = basename(project.dir)
        logger.info('applying patch to {} source'.format(src))

        environment = dict(os.environ)
        dirpath = tempfile.mkdtemp()
        patch_file = join(dirpath, 'patch')
        with open(patch_file, 'w') as file:
            for e, p in patch.items():
                file.write('{} {} {} {}\n'.format(*e))
                file.write(p + "\n")

        if self.config['semfix']:
            environment['ANGELIX_SEMFIX_MODE'] = 'YES'

        environment['ANGELIX_PATCH'] = patch_file

        with cd(project.dir):
            return_code = subprocess.call(['apply-patch', project.buggy],
                                          stderr=self.subproc_output,
                                          stdout=self.subproc_output,
                                          env=environment)
        if return_code != 0:
            logger.error("transformation of {} failed".format(relpath(project.dir)))
            raise TransformationError()

        shutil.rmtree(dirpath)

        pass
