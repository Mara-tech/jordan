from jordan_py import jordan
import sys
from time import time
import yaml

DEFAULT_CLIENT_NAME = f'cli-{int(time())}'
DEFAULT_FORWARD_STDOUT = False
DEFAULT_CONF_FILEPATH = './conf.yml'


import argparse
parser = argparse.ArgumentParser()
parser.add_argument("-u", "--url", required=False, help="Jordan server base URL", default=None)
parser.add_argument("-c", "--client", required=False, help="Define client name", default=DEFAULT_CLIENT_NAME)
parser.add_argument("-s", "-f", "--forward-stdout", required=False, help="Forward Jordan stdin to stdout", default=None, action='store_true')
parser.add_argument("-y", "--yml-conf", "--yaml-conf", "--conf", required=False, help="Path to configuration file (in YAML format)", default=DEFAULT_CONF_FILEPATH)
parser.add_argument("-t", "--test", required=False, help="Test current configuration. This option sends a default status to Jordan instance, but DOES NOT read stdin.", default=False, action='store_true')
args = parser.parse_args()


def read_config_file(filepath):
    try:
        with open(filepath, 'r') as conf_file:
            return yaml.load(conf_file, Loader=yaml.FullLoader)
    except FileNotFoundError:
        sys.stderr.write(f'Error while opening config file {filepath}.')
        sys.stderr.flush()
        return dict()


config = read_config_file(args.yml_conf)


def get_config_server_url():
    if 'url' in config:
        return config['url']
    return None


def get_config_forward_stdout():
    if 'forward-stdout' in config:
        return config['forward-stdout']
    return DEFAULT_FORWARD_STDOUT


def get_server_base_url():
    if args.url is None or len(args.url) == 0:
        url_from_config = get_config_server_url()
        if url_from_config is None or len(url_from_config) == 0:
            raise KeyError('Jordan Server base URL is not configured (neither in YAML conf file nor in argument).')
        return url_from_config
    return args.url


def client_name():
    return args.client


def forward_stdout():
    if args.forward_stdout:
        return args.forward_stdout
    return get_config_forward_stdout()


def is_test():
    return args.test


jordan_instance = jordan.register(get_server_base_url(), client_name=client_name())


def jordans(stream, **kwargs):
    for line in stream:
        jordan_instance.send_status(line, **kwargs)
        if forward_stdout():
            sys.stdout.write(line)
            sys.stdout.flush()


def main(*args, **kwargs):
    try:
        if is_test():
            jordans({"Default status for Jordan CLI testing"}, **kwargs)
        else:
            jordans(sys.stdin, **kwargs)
    finally:
        jordan_instance.unregister(**kwargs)

if __name__ == '__main__':
    main()