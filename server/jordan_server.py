# from threading import Thread
import sys

# Importing api is what validates every setting: check_configuration() runs at
# its import and raises ConfigurationError on a declaration that cannot be
# honoured. That is the whole of what --check below does.
import api as jordan_api

def start_api():
    print("Starting API")
    jordan_api.start_api()


def check_configuration():
    """Report that the settings in the environment would let a server start.

    Exists because the alternative is finding out from a deployment: a bad value
    stops the boot, the platform keeps the previous version serving, and the URL
    then answers with the *old* configuration while its logs describe that one.
    The error is explicit but written where nobody is looking, so the useful
    moment to read it is before deploying, on the values about to be set."""
    print("Configuration is usable: a server would start with these settings")


if __name__ == '__main__':
    if '--check' in sys.argv[1:]:
        # reaching this line means the import above accepted every setting
        check_configuration()
        sys.exit(0)
    #start API
    # Thread(target=start_api).start()
    start_api() #signals work on main thread only
