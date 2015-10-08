import os
import tempfile
import subprocess
from utils import cd
import logging


logger = logging.getLogger(__name__)


class RepairableTransformer:

    def __init__(self, config):
        self.config = config

    def __call__(self, project):
        src = os.path.basename(project.dir)
        logger.info('instrumenting repairable of {} source'.format(src))
        with cd(project.dir):
            subprocess.check_output(['instrument-repairable', project.buggy])

        
class SuspiciousTransformer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted

    def __call__(self, project, expressions):
        src = os.path.basename(project.dir)
        logger.info('instrumenting suspicious of {} source'.format(src))
        environment = dict(os.environ)
        suspicious_file = tempfile.NamedTemporaryFile(delete=False)
        for e in expressions:
            suspicious_file.write('{} {} {} {}\n'.format(*e))

         # UNIX only because we don't close file before passing it to another application
        suspicious_file.flush()
        os.fsync(suspicious_file.fileno())

        environment['ANGELIX_EXTRACTED'] = extracted
        environment['ANGELIX_SUSPICIOUS'] = suspicious_file.name

        with cd(project.dir):
            subprocess.check_output(['instrument-suspicious', project.buggy], env=environment)

        suspicious_file.close()
        os.remove(suspicious_file.naame)

    
class FixInjector:

    def __init__(self, config):
        self.config = config

    def __call__(self, project):
        src = os.path.basename(project.dir)
        logger.info('applying patch to {} source'.format(src))
        pass


