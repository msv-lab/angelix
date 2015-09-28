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
            code = proc.wait(timeout=self.config['test_timeout'])

        return code == 0                
            
                
