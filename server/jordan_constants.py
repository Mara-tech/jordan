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

# Name of the environment variable holding the shared admin token protecting
# the /jordan/admin/* namespace. The value itself is read at request time
# because server/.env is only loaded when rejson_interface is imported.
JORDAN_ADMIN_TOKEN_ENV_VAR = 'JORDAN_ADMIN_TOKEN'
