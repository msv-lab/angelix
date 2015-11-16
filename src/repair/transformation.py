import os
from os.path import join, basename
import tempfile
import subprocess
from utils import cd
import logging
import shutil


logger = logging.getLogger(__name__)


class RepairableTransformer:

    def __init__(self, config):
        self.config = config

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
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL
        with cd(project.dir):
            subprocess.check_output(['instrument-repairable', project.buggy],
                                    stderr=stderr,
                                    env=environment)


class SuspiciousTransformer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted

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

        environment['ANGELIX_EXTRACTED'] = self.extracted
        environment['ANGELIX_SUSPICIOUS'] = suspicious_file

        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(project.dir):
            subprocess.check_output(['instrument-suspicious', project.buggy],
                                    env=environment,
                                    stderr=stderr)

        shutil.rmtree(dirpath)


class FixInjector:

    def __init__(self, config):
        self.config = config

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

        environment['ANGELIX_PATCH'] = patch_file

        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(project.dir):
            subprocess.check_output(['apply-patch', project.buggy],
                                    env=environment,
                                    stderr=stderr)

        shutil.rmtree(dirpath)

        pass
