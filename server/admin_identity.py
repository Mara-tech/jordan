"""Operator identities, roles and permissions for the /jordan/admin/* namespace.

Operators are declared in the JORDAN_ADMIN_USERS environment variable, a JSON
array of accounts holding a *hashed* password:

    JORDAN_ADMIN_USERS=[{"login": "alice", "passwordHash": "pbkdf2:sha256:...", "role": "admin"}]

Generate an entry with:

    python admin_identity.py <login> <password> [role]

A declaration that cannot be honoured — malformed JSON, an account without a
passwordHash, an unknown role, a login declared twice — stops the server at
startup rather than quietly dropping that account and locking its holder out.

`POST /jordan/admin/login` exchanges those credentials for a session token stored
in Redis with a TTL (JORDAN_ADMIN_SESSION_TTL, 12 hours by default), so a leaked
token stops working on its own.

The shared JORDAN_ADMIN_TOKEN remains valid as a bootstrap / machine-to-machine
credential: every permission, but no named operator behind it.
"""

import json
import os
import sys

from werkzeug.security import check_password_hash, generate_password_hash

import jordan_log as log
from jordan_constants import (JORDAN_ADMIN_SESSION_TTL_ENV_VAR,
                              JORDAN_ADMIN_USERS_ENV_VAR,
                              JORDAN_DEFAULT_ADMIN_SESSION_TTL,
                              ConfigurationError)

# ── Roles and permissions ────────────────────────────────────────────────────

ROLE_VIEWER = 'viewer'
ROLE_OPERATOR = 'operator'
ROLE_ADMIN = 'admin'

PERMISSION_READ = 'read'      # list clients/actions, read statuses and messages
PERMISSION_SEND = 'send'      # send a message (command) to a passive client
PERMISSION_DELETE = 'delete'  # delete a task, a client, or the whole base

ROLE_PERMISSIONS = {
    ROLE_VIEWER: [PERMISSION_READ],
    ROLE_OPERATOR: [PERMISSION_READ, PERMISSION_SEND],
    ROLE_ADMIN: [PERMISSION_READ, PERMISSION_SEND, PERMISSION_DELETE],
}

# Login reported for calls authenticated with the shared JORDAN_ADMIN_TOKEN.
SHARED_TOKEN_LOGIN = 'shared-admin'


def public_identity(login, role):
    """Public view of an operator: stored in the session, returned to the caller,
    and used as the `author` of the messages they send."""
    return {'login': login, 'role': role, 'permissions': list(ROLE_PERMISSIONS.get(role, []))}


def shared_token_identity():
    """Identity behind JORDAN_ADMIN_TOKEN: every permission, no named operator."""
    return public_identity(SHARED_TOKEN_LOGIN, ROLE_ADMIN)


def has_permission(operator, permission):
    return permission in (operator or {}).get('permissions', [])


# ── Declared accounts ────────────────────────────────────────────────────────


def load_operators():
    """login -> declared account, from JORDAN_ADMIN_USERS.

    Empty when the variable is unset, or set to an empty array — declaring no
    named operator is a legitimate choice, the shared token then stands alone.

    Anything else that cannot be honoured raises ConfigurationError, which
    check_configuration() turns into a refusal to start. All or nothing on
    purpose: skipping the faulty entry would silently remove an account its
    operator believes exists, and locking someone out is exactly the kind of
    surprise nobody discovers before the day they need to log in."""
    raw = os.environ.get(JORDAN_ADMIN_USERS_ENV_VAR, '').strip()
    if not raw:
        return {}
    try:
        declared = json.loads(raw)
    except ValueError as invalid_json:
        raise ConfigurationError(
            f"{JORDAN_ADMIN_USERS_ENV_VAR} is not valid JSON") from invalid_json
    if not isinstance(declared, list):
        raise ConfigurationError(
            f"{JORDAN_ADMIN_USERS_ENV_VAR} must be a JSON array of accounts, as "
            f'[{{"login": ..., "passwordHash": ..., "role": ...}}]')

    operators = {}
    for position, entry in enumerate(declared, start=1):
        if not isinstance(entry, dict):
            raise ConfigurationError(
                f"{JORDAN_ADMIN_USERS_ENV_VAR}: account #{position} is not an object")
        login = entry.get('login')
        role = entry.get('role', ROLE_VIEWER)
        if not login or not entry.get('passwordHash'):
            raise ConfigurationError(
                f"{JORDAN_ADMIN_USERS_ENV_VAR}: account #{position} has no login or no "
                f"passwordHash — produce one with 'python admin_identity.py <login> <password>'")
        if role not in ROLE_PERMISSIONS:
            raise ConfigurationError(
                f"{JORDAN_ADMIN_USERS_ENV_VAR}: unknown role '{role}' for '{login}', "
                f"expected one of {', '.join(ROLE_PERMISSIONS)}")
        if login in operators:
            # a mapping would keep the last one, and the other password and role
            # would stop working without anything saying so
            raise ConfigurationError(
                f"{JORDAN_ADMIN_USERS_ENV_VAR}: '{login}' is declared twice")
        operators[login] = entry
    return operators


def authenticate(login, password):
    """Check a login/password pair, return the public identity or None."""
    if not login or not password:
        return None
    account = load_operators().get(login)
    if account is None:
        return None
    if not check_password_hash(account['passwordHash'], password):
        return None
    return public_identity(login, account.get('role', ROLE_VIEWER))


def session_ttl():
    """Lifetime in seconds of a token issued by /jordan/admin/login."""
    raw = os.environ.get(JORDAN_ADMIN_SESSION_TTL_ENV_VAR, '').strip()
    if not raw:
        return JORDAN_DEFAULT_ADMIN_SESSION_TTL
    try:
        ttl = int(raw)
    except ValueError:
        ttl = 0
    if ttl <= 0:
        log.error(f"{JORDAN_ADMIN_SESSION_TTL_ENV_VAR}='{raw}' is not a positive number of seconds, "
                  f"falling back to {JORDAN_DEFAULT_ADMIN_SESSION_TTL}s")
        return JORDAN_DEFAULT_ADMIN_SESSION_TTL
    return ttl


def hash_password(password):
    return generate_password_hash(password)


# ── Account declaration helper ───────────────────────────────────────────────


def _main(argv):
    if len(argv) < 3:
        print(f"usage: python {os.path.basename(argv[0])} <login> <password> [{'|'.join(ROLE_PERMISSIONS)}]")
        return 2
    login, password = argv[1], argv[2]
    role = argv[3] if len(argv) > 3 else ROLE_VIEWER
    if role not in ROLE_PERMISSIONS:
        print(f"unknown role '{role}', expected one of {', '.join(ROLE_PERMISSIONS)}")
        return 2
    entry = {'login': login, 'passwordHash': hash_password(password), 'role': role}
    print(f"Add this entry to the {JORDAN_ADMIN_USERS_ENV_VAR} JSON array:\n")
    print(json.dumps(entry))
    return 0


if __name__ == '__main__':
    sys.exit(_main(sys.argv))
