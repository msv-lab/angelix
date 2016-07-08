import os
from os.path import join, exists
import logging

logger = logging.getLogger(__name__)

class Dump:

    def _json_to_dump(self, json):
        for test, data in json.items():
            test_dir = join(self.dir, test)
            os.mkdir(test_dir)
            for variable, values in data.items():
                variable_dir = join(test_dir, variable)
                os.mkdir(variable_dir)
                for i, v in enumerate(values):
                    instance_file = join(variable_dir, str(i))
                    with open(instance_file, 'w') as file:
                        file.write(str(v))

    def export(self):
        json = dict()
        tests = os.listdir(self.dir)
        for test in tests:
            dump = self[test]
            json[test] = dict()
            vars = os.listdir(dump)
            for var in vars:
                instances = os.listdir(join(dump, var))
                json[test][var] = []
                for i in range(0, len(instances)):
                    file = join(dump, var, str(i))
                    with open(file) as f:
                        content = f.read()
                    json[test][var].append(content)
                if var == 'reachable':
                    json[test][var] = list(set(json[test][var]))

        return json

    def __init__(self, working_dir, correct_output):
        self.dir = join(working_dir, 'dump')
        os.mkdir(self.dir)
        if correct_output is not None:
            self._json_to_dump(correct_output)

    def __iadd__(self, test_id):
        dir = join(self.dir, test_id)
        os.mkdir(dir)
        return self

    def __getitem__(self, test_id):
        dir = join(self.dir, test_id)
        return dir

    def __contains__(self, test_id):
        dir = join(self.dir, test_id)
        if exists(dir):
            return True
        else:
            return False


class Trace:

    def __init__(self, working_dir):
        self.dir = join(working_dir, 'trace')
        os.mkdir(self.dir)

    def __iadd__(self, test_id):
        trace_file = join(self.dir, test_id)
        file = open(trace_file, 'w')
        file.close()
        return self

    def __getitem__(self, test_id):
        trace_file = join(self.dir, test_id)
        return trace_file

    def __contains__(self, test_id):
        trace_file = join(self.dir, test_id)
        if exists(trace_file):
            return True
        else:
            return False

    def parse(self, test_id):
        trace_file = join(self.dir, test_id)
        trace = []
        with open(trace_file) as file:
            for line in file:
                id = [int(s) for s in line.split()]
                assert len(id) == 4
                trace.append(tuple(id))
        return trace


class Load:

    def __init__(self, working_dir):
        self.dir = join(working_dir, 'load')
        os.mkdir(self.dir)

    def __getitem__(self, test_id):
        trace_file = join(self.dir, test_id)
        return trace_file
