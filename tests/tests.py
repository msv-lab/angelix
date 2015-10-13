import unittest
from subprocess import check_output
import sys
import os


class cd:
    """Context manager for changing the current working directory"""

    def __init__(self, newPath):
        self.newPath = os.path.expanduser(newPath)

    def __enter__(self):
        self.savedPath = os.getcwd()
        os.chdir(self.newPath)

    def __exit__(self, etype, value, traceback):
        os.chdir(self.savedPath)


script_dir = os.path.dirname(os.path.realpath(sys.argv[0]))


def run_angelix(args, dir):
    with cd(dir):
        output = check_output(['angelix', '--quiet'] + args)
        return str(output, 'UTF-8').strip()


class TestAngelix(unittest.TestCase):

    def test_condition(self):
        test_dir = os.path.join(script_dir, 'condition')
        args = ['src', 'test.c', 'oracle', 'tests.json', '--output', 'output.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_loop_condition(self):
        test_dir = os.path.join(script_dir, 'loop-condition')
        args = ['src', 'test.c', 'oracle', 'tests.json', '--output', 'output.json', '--klee-max-forks', '5']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_multiline(self):
        test_dir = os.path.join(script_dir, 'multiline')
        args = ['src', 'test.c', 'oracle', 'tests.json', '--output', 'output.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

      
if __name__ == '__main__':
    unittest.main()
