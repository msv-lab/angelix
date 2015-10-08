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


class TestAngelix(unittest.TestCase):

  def test_condition(self):
    test_dir = os.path.join(script_dir, 'condition')
    cmd = ['angelix', 'src', 'test.c', 'oracle', 'tests.json', '--output', 'output.json']
    with cd(test_dir):
      check_output(cmd)

      
if __name__ == '__main__':
    unittest.main()
