class Builder:

    def __init__(self, compilation_db, tests_compilation_db):
        self.compilation_db = compilation_db
        self.tests_compilation_db = tests_compilation_db

    def build_for_dumping(self, dir):
        pass

    def build_test_for_dumping(self, dir, test_case):
        pass

    def build_for_klee(self, dir):
        pass

    def build_test_for_klee(self, dir, test):
        pass
