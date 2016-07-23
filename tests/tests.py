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

    def test_if_condition(self):
        test_dir = os.path.join(script_dir, 'if-condition')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_loop_condition(self):
        test_dir = os.path.join(script_dir, 'loop-condition')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--klee-max-forks', '5', '--defect', 'loop-conditions']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_multiline(self):
        test_dir = os.path.join(script_dir, 'multiline')
        args = ['src', 'test.c', 'oracle', '0', '1', '2', '3', '4', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_memberexpr(self):
        test_dir = os.path.join(script_dir, 'memberexpr')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_golden(self):
        test_dir = os.path.join(script_dir, 'golden')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--golden', 'golden']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_assignment(self):
        test_dir = os.path.join(script_dir, 'assignment')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--defect', 'assignments']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')
        
    def test_reachability(self):
        test_dir = os.path.join(script_dir, 'reachability')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_for_loop(self):
        test_dir = os.path.join(script_dir, 'for-loop')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--klee-max-forks', '5', '--defect', 'loop-conditions']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_deletion(self):
        test_dir = os.path.join(script_dir, 'deletion')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--defect', 'guards']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')
        
    def test_guard(self):
        test_dir = os.path.join(script_dir, 'guard')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--defect', 'guards', '--synthesis-level', 'extended-inequalities', 'variables']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_enum(self):
        test_dir = os.path.join(script_dir, 'enum')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_long_output(self):
        test_dir = os.path.join(script_dir, 'long-output')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--defect', 'if-conditions']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    # def test_str_output(self):
    #     test_dir = os.path.join(script_dir, 'str-output')
    #     args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json', '--defect', 'if-conditions']
    #     result = run_angelix(args, test_dir)
    #     self.assertEqual(result, 'SUCCESS')

    def test_named_decl(self):
        test_dir = os.path.join(script_dir, 'named-decl')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_deletebreak(self):
        test_dir = os.path.join(script_dir, 'deletebreak')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '4', '--assert', 'assert.json', '--defect', 'guards']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_not_equal(self):
        test_dir = os.path.join(script_dir, 'not-equal')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '4', '--assert', 'assert.json', '--synthesis-levels', 'alternatives']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

    def test_arrays(self):
        test_dir = os.path.join(script_dir, 'array-subscripts')
        args = ['src', 'test.c', 'oracle', '1', '2', '3', '--assert', 'assert.json']
        result = run_angelix(args, test_dir)
        self.assertEqual(result, 'SUCCESS')

if __name__ == '__main__':
    unittest.main()
