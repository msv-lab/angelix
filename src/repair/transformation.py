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
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL
        with cd(project.dir):
            subprocess.check_output(['instrument-repairable', project.buggy], stderr=stderr)


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

    def __call__(self, project):
        src = basename(project.dir)
        logger.info('applying patch to {} source'.format(src))
        pass
