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
                 golden=None, dump=None, make_args='', config=None):
        if not(golden is None) and not(dump is None) \
           or golden is None and dump is None:
            raise Exception("error: golden source or correct dump must be provided (exclusively)")

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
        
        if golden is None:
            self.golden_source = None
        else:
            golden_dir = os.path.join(working_dir, "golden")
            init_repair_source(golden, golden_dir)
            self.golden_source = Golden(golden_dir, buggy, make_args, tests_spec)

        if dump is None:
            self.correct_dump = None
        else:
            self.correct_dump = os.path.join(working_dir, 'correct-dump')
            shutil.copytree(dump, self.correct_dump)

        self.dump = os.path.join(working_dir, 'dump')
        os.mkdir(self.dump)

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
    parser.add_argument('--golden', metavar='DIR', help='golden source directory')
    parser.add_argument('--dump', metavar='DIR', help='correct dump for failing test cases')
    parser.add_argument('--make-args', metavar='ARGS', default='', help='make arguments')
    parser.add_argument('--config', help='configuration file')
    
    args = parser.parse_args()

    repair = Repair(source = args.source,
                    buggy = args.buggy,
                    oracle = args.oracle,
                    tests = args.tests,
                    golden = args.golden,
                    dump = args.dump,
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
