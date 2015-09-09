import os


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


def flatten(list):
    return sum(list, [])


def unique(list):
    """Select unique elements (order preserving)"""
    seen = set()
    return [x for x in list if not (x in seen or seen.add(x))]

