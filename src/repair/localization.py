class Localizer:

    def __init__(self, config, lines):
        self.lines = lines
        self.config = config

    def __call__(self, positive_traces, negative_traces):
        return negative_traces[0]  # TODO
