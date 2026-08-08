"""Operator identities, roles and permissions for the /jordan/admin/* namespace.

Operators are declared in the JORDAN_ADMIN_USERS environment variable, a JSON
array of accounts holding a *hashed* password:

    JORDAN_ADMIN_USERS=[{"login": "alice", "passwordHash": "pbkdf2:sha256:...", "role": "admin"}]

Generate an entry with:

    python admin_identity.py <login> <password> [role]

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
                              JORDAN_DEFAULT_ADMIN_SESSION_TTL)

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

    Returns an empty mapping when the variable is unset or unusable: a malformed
    declaration must never widen access, it only leaves no account to log into."""
    raw = os.environ.get(JORDAN_ADMIN_USERS_ENV_VAR, '').strip()
    if not raw:
        return {}
    try:
        declared = json.loads(raw)
    except ValueError:
        log.error(f"{JORDAN_ADMIN_USERS_ENV_VAR} is not valid JSON: no operator account is usable")
        return {}
    if not isinstance(declared, list):
        log.error(f"{JORDAN_ADMIN_USERS_ENV_VAR} must be a JSON array of accounts")
        return {}

    operators = {}
    for entry in declared:
        if not isinstance(entry, dict):
            log.error(f"{JORDAN_ADMIN_USERS_ENV_VAR}: skipping an entry that is not an object")
            continue
        login = entry.get('login')
        role = entry.get('role', ROLE_VIEWER)
        if not login or not entry.get('passwordHash'):
            log.error(f"{JORDAN_ADMIN_USERS_ENV_VAR}: skipping an entry without login or passwordHash")
            continue
        if role not in ROLE_PERMISSIONS:
            log.error(f"{JORDAN_ADMIN_USERS_ENV_VAR}: unknown role '{role}' for '{login}', skipping")
            continue
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
