import unittest
from subprocess import check_output, DEVNULL
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
        output = check_output(['angelix'] + args, stderr=DEVNULL)
        return str(output, 'UTF-8').strip()


class TestAngelix(unittest.TestCase):

    def test_semfix(self):
        test_dir = os.path.join(script_dir, 'semfix')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--semfix', '--synthesis-level', 'variables']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_semfix_synthesis(self):
        test_dir = os.path.join(script_dir, 'semfix-synthesis')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '4', '5', '--assert', 'assert.json', '--lines', '47', '--semfix']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

if __name__ == '__main__':
    unittest.main()
