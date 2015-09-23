import os
from subprocess import Popen


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

    def __init__(self, working_dir, correct_dump):
        self.dir = os.path.join(working_dir, 'dump')
        os.mkdir(self.dir)
        if correct_dump is not None:
            shutil.copytree(correct_dump, self.dir)

    def __iadd__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        os.mkdir(dir)

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
        dir = os.path.join(self.dir, test_id)
        file = open(dir,'w')
        file.close()

    def __getitem__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        pass
        
    def __contains__(self, test_id):
        dir = os.path.join(self.dir, test_id)
        if os.path.exists(dir):
            return True
        else:
            return False

    def parse(self, test):
        # TODO
        pass


class Tester:

    def __init__(self, config, oracle):
        self.config = config
        self.oracle = oracle

    def __call__(project, test, dump=None, trace=None):
        environment = dict(os.environ)
        environment.update('ANGELIX_TEST', test)
        if dump is not None:
            environment.update('ANGELIX_DUMP', dump)
        if trace is not None:
            environment.update('ANGELIX_TRACE', trace)

        with cd(project.dir):
            proc =  Popen([self.oracle, test], env=environment)
            code = proc.wait(timeout=self.config['testing']['TestTimeout'])

        return code == 0



def flatten(list):
    return sum(list, [])


def unique(list):
    """Select unique elements (order preserving)"""
    seen = set()
    return [x for x in list if not (x in seen or seen.add(x))]

