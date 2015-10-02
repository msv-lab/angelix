class RepairableTransformer:

    def __init__(self, config):
        self.config = config

    def __call__(self, project):
        pass

        
class SuspiciousTransformer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted

    def __call__(self, project):
        pass

    
class FixInjector:

    def __init__(self, config):
        self.config = config

    def __call__(self, project):
        pass


