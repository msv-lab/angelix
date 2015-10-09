import copy
import difflib
import os
import shutil
import subprocess
import json
from utils import cd
import logging


logger = logging.getLogger(__name__)


class Project:

    def __init__(self, config, dir, buggy, build_cmd, tests_spec):
        self.config = config
        self.dir = dir
        self.buggy = buggy
        self.build_cmd = build_cmd
        self.tests_spec = tests_spec
        self._buggy_backup = os.path.join(self.dir, self.buggy) + '.backup'
        shutil.copyfile(os.path.join(self.dir, self.buggy), self._buggy_backup)

    def restore_buggy(self):
        shutil.copyfile(self._buggy_backup, os.path.join(self.dir, self.buggy))

    def diff_buggy(self):
        with open(os.path.join(self.dir, self.buggy),'r') as buggy:
            buggy_lines = buggy.readlines()
        with open(self._buggy_backup,'r') as backup:
            backup_lines = backup.readlines()
        return difflib.unified_diff(backup_lines, buggy_lines)

    def import_compilation_db(self, compilation_db):
        compilation_db = copy.deepcopy(compilation_db)
        for item in compilation_db:
            item['directory'] = os.path.join(self.dir, item['directory'])
            item['file'] = os.path.join(self.dir, item['file'])
            # TODO add clang headers to the command
        compilation_db_file = os.path.join(self.dir, 'compile_commands.json')
        with open(compilation_db_file, 'w') as file:
            json.dump(compilation_db, file)
        

class Validation(Project):

    def build(self):
        logger.info('building validation source')
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(self.dir):
            subprocess.check_output(self.build_cmd, shell=True, stderr=stderr)

    def build_test(self, test_case):
        pass

    def export_compilation_db(self):
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL
        with cd(self.dir):
            subprocess.check_output('bear ' + self.build_cmd, shell=True, stderr=stderr)
        compilation_db_file = os.path.join(self.dir, 'compile_commands.json')
        with open(compilation_db_file) as file:
            compilation_db = json.load(file)
        # making paths relative:
        for item in compilation_db:
            item['directory'] = os.path.relpath(item['directory'], self.dir)
            item['file'] = os.path.relpath(item['file'], self.dir)
        return compilation_db


class Frontend(Project):

    def build(self):
        logger.info('building frontend source')
        environment = dict(os.environ)
        environment['CC'] = 'angelix-compiler --test'
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        with cd(self.dir):
            subprocess.check_output(self.build_cmd, env=environment, shell=True, stderr=stderr)

    def build_test(self, test_case):
        pass


class Backend(Project):

    def build(self):
        logger.info('building backend source')
        environment = dict(os.environ)
        environment['CC'] = 'angelix-compiler --klee'
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL
        
        with cd(self.dir):
            subprocess.check_output(self.build_cmd, env=environment, shell=True, stderr=stderr)

    def build_test(self, test_case):
        pass


class Golden(Project):

    def build(self):
        logger.info('building golden source')
        environment = dict(os.environ)
        environment['CC'] = 'angelix-compiler --test'
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL
        
        with cd(self.dir):
            subprocess.check_output(self.build_cmd, env=environment, shell=True, stderr=stderr)

    def build_test(self, test_case):
        pass
