import os
import socket


IPAddr = socket.gethostbyname(socket.gethostname())

JORDAN_API_HOST = '0.0.0.0'
JORDAN_API_PORT = int(os.environ.get('PORT', 5000))
JORDAN_API_PROTOCOL = 'http'
JORDAN_API_PATH_PREFIX = '/jordan'
JORDAN_API_URL_PREFIX = f"{JORDAN_API_PROTOCOL}://{IPAddr}:{JORDAN_API_PORT}{JORDAN_API_PATH_PREFIX}"

JORDAN_OPEN_API_DOC_SUFFIX = JORDAN_API_PATH_PREFIX + '/swagger-ui'
JORDAN_OPEN_API_URL = f"{JORDAN_API_PROTOCOL}://{IPAddr}:{JORDAN_API_PORT}{JORDAN_OPEN_API_DOC_SUFFIX}"

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
