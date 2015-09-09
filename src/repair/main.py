import configparser
import os
import shutil
import argparse
import time
import json

from project import Validation, Frontend, Backend
from utils import format_time
from testing import Tester
from compilation import Builder


class Repair:

    def __init__(self, source, buggy, oracle, tests,
                 correct_source=None, correct_dump=None, make_args='', config=None):
        if not(correct_source is None) and not(correct_dump is None) \
           or correct_source is None and correct_dump is None:
            raise Exception("error: correct source or correct dump must be provided (exclusively)")

        defaults = os.path.join(os.environ['ANGELIX_ROOT'], 'defaults.cfg')
        self.config = configparser.ConfigParser()
        self.config.read_file(open(defaults))
        if not(config is None):
            self.config.read(config)

        working_dir = os.path.join(os.getcwd(), ".angelix")
        if os.path.exists(working_dir):
            shutil.rmtree(working_dir)
        os.makedirs(working_dir)

        with open(tests) as tests_file:
            tests_spec = json.load(tests_file)
        self.tester = Tester(tests_spec)

        validation_dir = os.path.join(working_dir, "validation")
        shutil.copytree(source, validation_dir)
        shutil.copy(oracle, validation_dir)
        self.validation_source = Validation(validation_dir, buggy, make_args, tests_spec)
        self.validation_source.build_compilation_db()

        def init_repair_source(src, dst):
            shutil.copytree(src, dst)
            shutil.copyfile(oracle, os.path.join(dst, 'oracle'))
            shutil.copy(os.path.join(validation_dir, 'compile_commands.json'), dst)

        frontend_dir = os.path.join(working_dir, "frontend")
        init_repair_source(source, frontend_dir)
        self.frontend_source = Frontend(frontend_dir, buggy, make_args, tests_spec)
        
        backend_dir = os.path.join(working_dir, "backend")
        init_repair_source(source, backend_dir)
        self.backend_source = Backend(backend_dir, buggy, make_args, tests_spec)
        
        if correct_source is None:
            self.correct_source = None
        else:
            correct_dir = os.path.join(working_dir, "correct")
            init_repair_source(correct_source, correct_dir)
            self.correct_source = Correct(correct_dir, buggy, make_args, tests_spec)

        if correct_dump is None:
            self.correct_dump = None
        else:
            self.correct_dump = os.path.join(working_dir, 'correct-dump')
            shutil.copytree(correct_dump, self.correct_dump)

        os.mkdir(os.path.join(working_dir, 'dump'))

    def generate(self):
        self.meta = dict()
        start = time.time()
        patch = self._generate()
        end = time.time()
        self.meta['time'] = end - start
        return patch

    def _generate(self):
        return None


if __name__ == "__main__":

    parser = argparse.ArgumentParser()
    parser.add_argument('--source', metavar='DIR', help='source directory', required=True)
    parser.add_argument('--buggy', metavar='FILE', help='relative path to buggy file', required=True)
    parser.add_argument('--oracle', metavar='FILE', help='oracle script', required=True)
    parser.add_argument('--tests', metavar='FILE', help='tests JSON database', required=True)
    parser.add_argument('--correct-source', metavar='DIR', help='correct source directory')
    parser.add_argument('--correct-dump', metavar='DIR', help='correct dump for failing test cases')
    parser.add_argument('--make-args', metavar='ARGS', default='', help='make arguments')
    parser.add_argument('--config', help='configuration file')
    
    args = parser.parse_args()

    repair = Repair(source = args.source,
                    buggy = args.buggy,
                    oracle = args.oracle,
                    tests = args.tests,
                    correct_source = args.correct_source,
                    correct_dump = args.correct_dump,
                    make_args = args.make_args,
                    config = args.config)

    patch = repair.generate()

    elapsed = format_time(repair.meta['time'])
    if patch is None:
        print("Failed to generate patch in {}".format(elapsed))
    else:
        print("Patch is successfully generated in {} (see generated.diff)".format(elapsed))
        with open('generated.diff', 'w+') as file:
            for line in patch:
                file.write(line)
