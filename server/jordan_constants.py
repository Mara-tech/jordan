import os
import socket


class ConfigurationError(Exception):
    """A server setting is present but unusable.

    Raised where the setting is read, and turned into a refusal to start by
    check_configuration() in api.py — so the mistake costs a failed boot instead
    of a behaviour nobody asked for. Lives here because both api.py and
    admin_identity.py raise it."""


TRUE_VALUES = ('1', 'true', 'yes', 'on')
FALSE_VALUES = ('0', 'false', 'no', 'off')


def parse_bool(raw):
    """True or False for the usual spellings of yes and no, None for anything
    else — including an empty value.

    What an unreadable value costs is the caller's decision, and it differs:
    a switch that only ever grants something (the debugger, the docs) falls back
    to its default and logs, while one whose default is the unsafe side
    (REDIS_SSL) refuses to start. A misspelled 'true' must never be the reason
    the Redis password travels in clear."""
    value = raw.strip().lower()
    if value in TRUE_VALUES:
        return True
    if value in FALSE_VALUES:
        return False
    return None


IPAddr = socket.gethostbyname(socket.gethostname())

JORDAN_API_HOST = '0.0.0.0'
JORDAN_API_PORT = int(os.environ.get('PORT', 5000))
JORDAN_API_PROTOCOL = 'http'
JORDAN_API_PATH_PREFIX = '/jordan'
JORDAN_API_URL_PREFIX = f"{JORDAN_API_PROTOCOL}://{IPAddr}:{JORDAN_API_PORT}{JORDAN_API_PATH_PREFIX}"

JORDAN_OPEN_API_DOC_SUFFIX = JORDAN_API_PATH_PREFIX + '/swagger-ui'
JORDAN_OPEN_API_URL = f"{JORDAN_API_PROTOCOL}://{IPAddr}:{JORDAN_API_PORT}{JORDAN_OPEN_API_DOC_SUFFIX}"

# Name of the environment variable deciding whether the connection to Redis is
# encrypted (see rejson_interface). Off by default, which is right for a Redis
# reachable over a loopback or a private network and wrong for a managed one
# reached over the internet — hence the refusal to start on a value that reads
# as neither.
REDIS_SSL_ENV_VAR = 'REDIS_SSL'

# Names of the environment variables deciding how much of itself the server
# exposes. Both default to off, so a deployment that declares nothing serves the
# API and nothing else.
#
#   JORDAN_DEBUG        Werkzeug debugger on the development server
#   JORDAN_ENABLE_DOCS  publish Swagger UI and the OpenAPI spec it reads;
#                       follows JORDAN_DEBUG when left unset
JORDAN_DEBUG_ENV_VAR = 'JORDAN_DEBUG'
JORDAN_ENABLE_DOCS_ENV_VAR = 'JORDAN_ENABLE_DOCS'

# Names of the environment variables guarding the /jordan/admin/* namespace.
# Their values are read at request time because server/.env is only loaded when
# rejson_interface is imported, after this module.
#
#   JORDAN_ADMIN_TOKEN       shared bootstrap token, full permissions, no identity
#   JORDAN_ADMIN_USERS       JSON array of operator accounts (see admin_identity.py)
#   JORDAN_ADMIN_SESSION_TTL lifetime in seconds of a token issued by /admin/login
JORDAN_ADMIN_TOKEN_ENV_VAR = 'JORDAN_ADMIN_TOKEN'
JORDAN_ADMIN_USERS_ENV_VAR = 'JORDAN_ADMIN_USERS'
JORDAN_ADMIN_SESSION_TTL_ENV_VAR = 'JORDAN_ADMIN_SESSION_TTL'
JORDAN_DEFAULT_ADMIN_SESSION_TTL = 12 * 60 * 60  # 12 hours

# Names of the environment variables guarding POST /jordan/client/register.
# Read at request time as well.
#
#   JORDAN_REGISTRATION_KEY          key a passive client must send to register;
#                                    unset means registration stays open
#   JORDAN_REGISTRATION_RATE_LIMIT   max registration attempts per caller and per
#                                    window; 0 (or less) disables the check
#   JORDAN_REGISTRATION_RATE_WINDOW  length of that window, in seconds
JORDAN_REGISTRATION_KEY_ENV_VAR = 'JORDAN_REGISTRATION_KEY'
JORDAN_REGISTRATION_RATE_LIMIT_ENV_VAR = 'JORDAN_REGISTRATION_RATE_LIMIT'
JORDAN_REGISTRATION_RATE_WINDOW_ENV_VAR = 'JORDAN_REGISTRATION_RATE_WINDOW'
JORDAN_DEFAULT_REGISTRATION_RATE_LIMIT = 20
JORDAN_DEFAULT_REGISTRATION_RATE_WINDOW = 60  # seconds
