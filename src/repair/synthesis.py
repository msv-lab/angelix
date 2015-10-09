import logging


logger = logging.getLogger(__name__)


class SynthesisTimeout(Exception):
    pass


class Synthesizer:

    def __init__(self, config, extracted):
        self.config = config
        self.extracted = extracted

    def __call__(self, angelic_forest):
        logger.warning('timeout when synthesizing fix')
        return None
