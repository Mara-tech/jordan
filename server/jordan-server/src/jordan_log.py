import yaml
import logging
import logging.config


with open('logging.yml', 'r') as stream:
    config = yaml.load(stream, Loader=yaml.FullLoader)

logging.config.dictConfig(config)
aws_logger = logging.getLogger('awsLogger')


def jordan_log(msg, tag=None, level=logging.INFO, logger=aws_logger, **kwargs):
    if msg is None:
        raise KeyError('Must provide a msg to log')
    message = f'[{tag}] {msg}' if tag is not None else msg
    logger.log(level, message)


def debug(message):
    jordan_log(message, level=logging.DEBUG)


def info(message):
    jordan_log(message, level=logging.INFO)


def error(message):
    jordan_log(message, level=logging.ERROR)