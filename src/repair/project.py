import difflib
import os
import shutil
import subprocess
from utils import cd


class Project:

    def __init__(self, dir, buggy, tests_spec):
        self.dir = dir
        self.buggy = buggy
        self.tests_spec = tests_spec
        self._buggy_backup = os.path.join(self.dir, self.buggy) + '.backup'
        shutil.copyfile(os.path.join(self.dir, self.buggy), self._buggy_backup)

    def restore_buggy():
        shutil.copyfile(self._buggy_backup, os.path.join(self.dir, self.buggy))

    def diff_buggy(self):
        with open(os.path.join(self.dir, self.buggy),'r') as buggy:
            buggy_lines = buggy.readlines()
        with open(self._buggy_backup,'r') as backup:
            backup_lines = backup.readlines()
        return difflib.unified_diff(backup_lines, buggy_lines)
        

class Validation(Project):

    def build(self):
        pass

    def build_test(self, test_case):
        pass

    def build_compilation_db(self):
        with cd(self.dir):
            subprocess.check_output(['bear make'], shell=True)


class Frontend(Project):

    def build(self):
        pass

    def build_test(self, test_case):
        pass


class Backend(Project):

    def build(self):
        pass

    def build_test(self, test_case):
        pass


class Golden(Project):

    def build(self):
        pass

    def build_test(self, test_case):
        pass
