import copy
import difflib
import os
from os.path import join, exists, relpath
import shutil
import subprocess
import json
from utils import cd
import logging
import tempfile


logger = logging.getLogger(__name__)


class CompilationError(Exception):
    pass


class Project:

    def __init__(self, config, dir, buggy, build_cmd, tests_spec):
        self.config = config
        self.dir = dir
        self.buggy = buggy
        self.build_cmd = build_cmd
        self.tests_spec = tests_spec
        self._buggy_backup = join(self.dir, self.buggy) + '.backup'
        shutil.copyfile(join(self.dir, self.buggy), self._buggy_backup)

    def restore_buggy(self):
        shutil.copyfile(self._buggy_backup, join(self.dir, self.buggy))

    def diff_buggy(self):
        with open(join(self.dir, self.buggy)) as buggy:
            buggy_lines = buggy.readlines()
        with open(self._buggy_backup) as backup:
            backup_lines = backup.readlines()
        return difflib.unified_diff(backup_lines, buggy_lines,
                                    fromfile=join('a', self.buggy),
                                    tofile=join('b', self.buggy))

    def import_compilation_db(self, compilation_db):
        compilation_db = copy.deepcopy(compilation_db)
        for item in compilation_db:
            item['directory'] = join(self.dir, item['directory'])
            item['file'] = join(self.dir, item['file'])
            item['command'] = item['command'] + ' -I' + os.environ['LLVM3_INCLUDE_PATH']
            # TODO add clang headers to the command
        compilation_db_file = join(self.dir, 'compile_commands.json')
        with open(compilation_db_file, 'w') as file:
            json.dump(compilation_db, file, indent=2)


def build_in_env(dir, cmd, verbose, env=os.environ):
    dirpath = tempfile.mkdtemp()
    messages = join(dirpath, 'messages')

    environment = dict(env)
    environment['ANGELIX_COMPILER_MESSAGES'] = messages

    if verbose:
        stderr = None
    else:
        stderr = subprocess.DEVNULL

    try:
        with cd(dir):
            subprocess.check_output(cmd, env=environment, shell=True, stderr=stderr)
    except subprocess.CalledProcessError:
        logger.warning("compilation of {} returned non-zero code".format(relpath(dir)))

    if exists(messages):
        with open(messages) as file:
            logger.warning("failed to build {}".format(relpath(file.readline().strip())))


def build_with_cc(dir, cmd, verbose, cc):
    env = dict(os.environ)
    env['CC'] = cc
    build_in_env(dir, cmd, verbose, env)


class Validation(Project):

    def build(self):
        logger.info('building validation source')
        build_in_env(self.dir,
                     self.build_cmd,
                     self.config['verbose'])

    def build_test(self, test_case):
        if 'build' in self.tests_spec[test_case]:
            build_in_env(join(self.dir, self.tests_spec[test_case]['build']['directory']),
                         self.tests_spec[test_case]['build']['command'],
                         self.config['verbose'])
        dependency = join(self.dir, self.tests_spec[test_case]['executable'])
        if not exists(dependency):
            logger.error("failed to build test {} dependency {}".format(test_case, relpath(dependency)))
            raise CompilationError()

    def export_compilation_db(self):
        logger.info('building json compilation database from validation source')

        build_in_env(self.dir,
                     'bear ' + self.build_cmd,
                     self.config['verbose'])

        compilation_db_file = join(self.dir, 'compile_commands.json')
        with open(compilation_db_file) as file:
            compilation_db = json.load(file)
        # making paths relative:
        for item in compilation_db:
            item['directory'] = relpath(item['directory'], self.dir)
            item['file'] = relpath(item['file'], self.dir)
        return compilation_db


class Frontend(Project):

    def build(self):
        logger.info('building frontend source')
        build_with_cc(self.dir,
                      self.build_cmd,
                      self.config['verbose'],
                      'angelix-compiler --test')

    def build_test(self, test_case):
        if 'build' in self.tests_spec[test_case]:
            build_with_cc(join(self.dir, self.tests_spec[test_case]['build']['directory']),
                          self.tests_spec[test_case]['build']['command'],
                          self.config['verbose'],
                          'angelix-compiler --test')
        dependency = join(self.dir, self.tests_spec[test_case]['executable'])
        if not exists(dependency):
            logger.error("failed to build test {} dependency {}".format(test_case,
                                                                        relpath(dependency)))
            raise CompilationError()


class Backend(Project):

    def build(self):
        logger.info('building backend source')
        build_with_cc(self.dir,
                      self.build_cmd,
                      self.config['verbose'],
                      'angelix-compiler --klee')

    def build_test(self, test_case):
        if 'build' in self.tests_spec[test_case]:
            build_with_cc(join(self.dir, self.tests_spec[test_case]['build']['directory']),
                          self.tests_spec[test_case]['build']['command'],
                          self.config['verbose'],
                          'angelix-compiler --klee')
        executable = join(self.dir, self.tests_spec[test_case]['executable'])
        dependency = executable + '.bc'
        if not exists(dependency):
            logger.error("failed to build test {} dependency {}".format(test_case,
                                                                        relpath(dependency)))
            raise CompilationError()
        patched_dependency = executable + '.patched.bc'
        if self.config['verbose']:
            stderr = None
        else:
            stderr = subprocess.DEVNULL

        try:
            subprocess.check_output(['angelix-patch-bitcode', dependency], stderr=stderr)
        except subprocess.CalledProcessError:        
            logger.warning("patching of {} returned non-zero code".format(relpath(dependency)))

        if not exists(patched_dependency):
            logger.error("failed to build test {} dependency {}".format(test_case,
                                                                        relpath(patched_dependency)))
            raise CompilationError()


class Golden(Project):

    def build(self):
        logger.info('building golden source')
        build_with_cc(self.dir,
                      self.build_cmd,
                      self.config['verbose'],
                      'angelix-compiler --test')

    def build_test(self, test_case):
        if 'build' in self.tests_spec[test_case]:
            build_with_cc(join(self.dir, self.tests_spec[test_case]['build']['directory']),
                          self.tests_spec[test_case]['build']['command'],
                          self.config['verbose'],
                          'angelix-compiler --test')
        dependency = join(self.dir, self.tests_spec[test_case]['executable'])
        if not exists(dependency):
            logger.error("failed to build test {} dependency {}".format(test_case,
                                                                        relpath(dependency)))
            raise CompilationError()
