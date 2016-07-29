import copy
import difflib
import os
from os.path import join, exists, relpath, basename, realpath
import shutil
import subprocess
import json
from utils import cd
import logging
import tempfile
import sys
import re
import statistics
import time


logger = logging.getLogger(__name__)


class CompilationError(Exception):
    pass


class Project:

    def __init__(self, config, dir, buggy, build_cmd, configure_cmd):
        self.config = config
        if self.config['verbose']:
            self.subproc_output = sys.stderr
        else:
            self.subproc_output = subprocess.DEVNULL
        self.dir = dir
        self.buggy = buggy
        self.build_cmd = build_cmd
        self.configure_cmd = configure_cmd
        self._buggy_backup = join(self.dir, self.buggy) + '.backup'
        shutil.copyfile(join(self.dir, self.buggy), self._buggy_backup)

    def restore_buggy(self):
        shutil.copyfile(self._buggy_backup, join(self.dir, self.buggy))

    def diff_buggy(self):
        with open(join(self.dir, self.buggy), encoding='latin-1') as buggy:
            buggy_lines = buggy.readlines()
        with open(self._buggy_backup, encoding='latin-1') as backup:
            backup_lines = backup.readlines()
        return difflib.unified_diff(backup_lines, buggy_lines,
                                    fromfile=join('a', self.buggy),
                                    tofile=join('b', self.buggy))

    def import_compilation_db(self, compilation_db):
        compilation_db = copy.deepcopy(compilation_db)
        for item in compilation_db:
            item['directory'] = join(self.dir, item['directory'])
            item['file'] = join(self.dir, item['file'])
            # this is a temporary hack. It general case, we need (probably) a different workflow:
            wrong_dir = realpath(join(self.dir, '..', 'validation'))
            item['command'] = item['command'].replace(wrong_dir, self.dir)

            item['command'] = item['command'] + ' -I' + os.environ['LLVM3_INCLUDE_PATH']
            # this is a hack to skip output expressions when perform transformation:
            item['command'] = item['command'] + ' -include ' + os.environ['ANGELIX_RUNTIME_H']
            item['command'] = item['command'] + ' -D ANGELIX_INSTRUMENTATION'
        compilation_db_file = join(self.dir, 'compile_commands.json')
        with open(compilation_db_file, 'w') as file:
            json.dump(compilation_db, file, indent=2)

    def configure(self):
        compile_start_time = time.time()
        src = basename(self.dir)
        logger.info('configuring {} source'.format(src))
        if self.configure_cmd is None:
            return
        with cd(self.dir):
            return_code = subprocess.call(self.configure_cmd,
                                          shell=True,
                                          stderr=self.subproc_output,
                                          stdout=self.subproc_output)
        if return_code != 0 and not self.config['mute_warning']:
                logger.warning("configuration of {} returned non-zero code".format(relpath(dir)))
        compile_end_time = time.time()
        compile_elapsed = compile_end_time - compile_start_time
        statistics.data['time']['compilation'] += compile_elapsed


def build_in_env(dir, cmd, subproc_output, config, env=os.environ):
    dirpath = tempfile.mkdtemp()
    messages = join(dirpath, 'messages')

    environment = dict(env)
    environment['ANGELIX_COMPILER_MESSAGES'] = messages

    with cd(dir):
        return_code = subprocess.call(cmd,
                                      env=environment,
                                      shell=True,
                                      stderr=subproc_output,
                                      stdout=subproc_output)
    if return_code != 0 and not config['mute_warning']:
        logger.warning("compilation of {} returned non-zero code".format(relpath(dir)))

    if exists(messages):
        with open(messages) as file:
            lines = file.readlines()
        if not config['mute_warning']:
            for line in lines:
                logger.warning("failed to build {}".format(relpath(line.strip())))


def build_with_cc(dir, cmd, stderr, cc, config):
    env = dict(os.environ)
    env['CC'] = cc
    build_in_env(dir, cmd, stderr, config, env)


class Validation(Project):

    def build(self):
        logger.info('building {} source'.format(basename(self.dir)))
        compile_start_time = time.time()
        build_in_env(self.dir, self.build_cmd,
                     subprocess.DEVNULL if self.config['mute_build_message']
                     else self.subproc_output,
                     self.config)
        compile_end_time = time.time()
        compile_elapsed = compile_end_time - compile_start_time
        statistics.data['time']['compilation'] += compile_elapsed


    def export_compilation_db(self):
        logger.info('building json compilation database from {} source'.format(basename(self.dir)))
        compile_start_time = time.time()
        build_in_env(self.dir,
                     'bear ' + self.build_cmd,
                     subprocess.DEVNULL if self.config['mute_build_message']
                     else self.subproc_output,
                     self.config)
        compile_end_time = time.time()
        compile_elapsed = compile_end_time - compile_start_time
        statistics.data['time']['compilation'] += compile_elapsed


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
        compile_start_time = time.time()
        build_with_cc(self.dir,
                      self.build_cmd,
                      subprocess.DEVNULL if self.config['mute_build_message']
                      else self.subproc_output,
                      'angelix-compiler --test',
                      self.config)
        compile_end_time = time.time()
        compile_elapsed = compile_end_time - compile_start_time
        statistics.data['time']['compilation'] += compile_elapsed



class Backend(Project):

    def build(self):
        logger.info('building {} source'.format(basename(self.dir)))
        compile_start_time = time.time()
        build_with_cc(self.dir,
                      self.build_cmd,
                      subprocess.DEVNULL if self.config['mute_build_message']
                      else self.subproc_output,
                      'angelix-compiler --klee',
                      self.config)
        compile_end_time = time.time()
        compile_elapsed = compile_end_time - compile_start_time
        statistics.data['time']['compilation'] += compile_elapsed
