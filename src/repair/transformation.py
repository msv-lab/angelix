class RepairableTransformer:

    def __init__(self, config):
        self.config = config

        
class SuspiciousTransformer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted


class FixInjector:

    def __init__(self, config):
        self.config = config

