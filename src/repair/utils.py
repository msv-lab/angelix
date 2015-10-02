import os
from subprocess import Popen
from contextlib import contextmanager


def format_time(seconds):
    m, s = divmod(seconds, 60)
    h, m = divmod(m, 60)
    hs = '{}h '.format(int(h)) if int(h) > 0 else ''
    ms = '{}m '.format(int(m)) if int(m) > 0 or int(h) > 0 else ''
    ss = '{}s'.format(int(s))
    return '{}{}{}'.format(hs, ms, ss)


class cd:
    """Context manager for changing the current working directory"""

    def __init__(self, newPath):
        self.newPath = os.path.expanduser(newPath)

    def __enter__(self):
        self.savedPath = os.getcwd()
        os.chdir(self.newPath)

    def __exit__(self, etype, value, traceback):
        os.chdir(self.savedPath)


class IdGenerator:

    def __init__(self):
        self.next = 0

    def next(self):
        self.next = self.next + 1
        return self.next - 1


class Dump:

    def _json_to_dump(self, json):
        for test, data in json.items():
            test_dir = os.path.join(self.dir, test)
            os.mkdir(test_dir)
            for variable, values in data.items():
                variable_dir = os.path.join(test_dir, variable)
                os.mkdir(variable_dir)
                for i, v in enumerate(values):
                    instance_file = os.path.join(variable_dir, str(i))
                    with open(instance_file, 'w') as file:
                        file.write(str(v))

    def __init__(self, working_dir, correct_output):
        self.dir = os.path.join(working_dir, 'dump')
        os.mkdir(self.dir)
        if correct_output is not None:
            self._json_to_dump(correct_output)

    def __iadd__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        os.mkdir(dir)
        return self

    def __getitem__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        return dir
        
    def __contains__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        if os.path.exists(dir):
            return True
        else:
            return False


class Trace:

    def __init__(self, working_dir):
        self.dir = os.path.join(working_dir, 'trace')
        os.mkdir(self.dir)

    def __iadd__(self, test_id):
        trace_file = os.path.join(self.dir, test_id)
        file = open(trace_file,'w')
        file.close()
        return self

    def __getitem__(self, test_id):
        trace_file = os.path.join(self.dir, test_id)
        return trace_file
        
    def __contains__(self, test_id):
        trace_file = os.path.join(self.dir, test_id)
        if os.path.exists(trace_file):
            return True
        else:
            return False

    def parse(self, test_id):
        trace_file = os.path.join(self.dir, test_id)
        trace = []
        with open(trace_file) as file:
            for line in file:
                id = [int(s) for s in line.split()]
                assert len(id) == 4
                trace.append(tuple(id))
        return trace


class TimeoutException(Exception): pass
import signal

# Note that this is UNIX only
@contextmanager
def time_limit(seconds):
    def signal_handler(signum, frame):
        raise TimeoutException("Timed out!")
    signal.signal(signal.SIGALRM, signal_handler)
    signal.alarm(seconds)
    try:
        yield
    finally:
        signal.alarm(0)


def flatten(list):
    return sum(list, [])


def unique(list):
    """Select unique elements (order preserving)"""
    seen = set()
    return [x for x in list if not (x in seen or seen.add(x))]

