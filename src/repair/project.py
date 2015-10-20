import copy
import difflib
import os
from os.path import join, exists, relpath, basename
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

    def __init__(self, config, dir, buggy, build_cmd, configure_cmd):
        self.config = config
        if self.config['verbose']:
            self.stderr = None
        else:
            self.stderr = subprocess.DEVNULL
        self.dir = dir
        self.buggy = buggy
        self.build_cmd = build_cmd
        self.configure_cmd = configure_cmd
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

    def configure(self):
        src = basename(self.dir)
        logger.info('configuring {} source'.format(src))
        if self.configure_cmd is None:
            return
        try:
            with cd(self.dir):
                subprocess.check_output(self.configure_cmd, shell=True, stderr=self.stderr)
        except subprocess.CalledProcessError:
            logger.warning("configuration of {} returned non-zero code".format(relpath(dir)))


def build_in_env(dir, cmd, stderr, env=os.environ):
    dirpath = tempfile.mkdtemp()
    messages = join(dirpath, 'messages')

    environment = dict(env)
    environment['ANGELIX_COMPILER_MESSAGES'] = messages

    try:
        with cd(dir):
            subprocess.check_output(cmd, env=environment, shell=True, stderr=stderr)
    except subprocess.CalledProcessError:
        logger.warning("compilation of {} returned non-zero code".format(relpath(dir)))

    if exists(messages):
        with open(messages) as file:
            lines = file.readlines()
        for line in lines:
            logger.warning("failed to build {}".format(relpath(line.strip())))


def build_with_cc(dir, cmd, stderr, cc):
    env = dict(os.environ)
    env['CC'] = cc
    build_in_env(dir, cmd, stderr, env)


class Validation(Project):

    def build(self):
        logger.info('building {} source'.format(basename(self.dir)))
        build_in_env(self.dir, self.build_cmd, self.stderr)

    def export_compilation_db(self):
        logger.info('building json compilation database from {} source'.format(basename(self.dir)))

        build_in_env(self.dir,
                     'bear ' + self.build_cmd,
                     self.stderr)

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
        logger.info('building {} source'.format(basename(self.dir)))
        build_with_cc(self.dir,
                      self.build_cmd,
                      self.stderr,
                      'angelix-compiler --test')


class Backend(Project):

    def build(self):
        logger.info('building {} source'.format(basename(self.dir)))
        build_with_cc(self.dir,
                      self.build_cmd,
                      self.stderr,
                      'angelix-compiler --klee')
