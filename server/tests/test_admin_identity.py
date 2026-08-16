"""Unit tests for the operator accounts, roles and permissions of admin_identity."""

import json

import pytest
from werkzeug.security import generate_password_hash

import admin_identity as identity
from jordan_constants import ConfigurationError

TEST_HASH_METHOD = 'pbkdf2:sha256:1000'


def declare(monkeypatch, *accounts):
    monkeypatch.setenv('JORDAN_ADMIN_USERS', json.dumps(list(accounts)))


def account(login='alice', password='pwd', role=identity.ROLE_ADMIN):
    return {
        'login': login,
        'passwordHash': generate_password_hash(password, method=TEST_HASH_METHOD),
        'role': role,
    }


# ── Roles ─────────────────────────────────────────────────────────────────────


@pytest.mark.parametrize('role,permissions', [
    (identity.ROLE_VIEWER, ['read']),
    (identity.ROLE_OPERATOR, ['read', 'send']),
    (identity.ROLE_ADMIN, ['read', 'send', 'delete']),
])
def test_role_grants_its_permissions(role, permissions):
    assert identity.public_identity('someone', role)['permissions'] == permissions


def test_has_permission():
    viewer = identity.public_identity('vic', identity.ROLE_VIEWER)
    assert identity.has_permission(viewer, identity.PERMISSION_READ)
    assert not identity.has_permission(viewer, identity.PERMISSION_DELETE)


def test_has_permission_on_missing_identity():
    assert not identity.has_permission(None, identity.PERMISSION_READ)


def test_shared_token_identity_has_every_permission():
    shared = identity.shared_token_identity()
    assert shared['login'] == identity.SHARED_TOKEN_LOGIN
    assert shared['permissions'] == ['read', 'send', 'delete']


# ── Declared accounts ─────────────────────────────────────────────────────────


def test_no_declaration_means_no_account(monkeypatch):
    monkeypatch.delenv('JORDAN_ADMIN_USERS', raising=False)
    assert identity.load_operators() == {}


def test_an_empty_array_declares_no_account(monkeypatch):
    """A legitimate choice: no named operator, the shared token stands alone —
    which is what docker-compose.yml passes by default."""
    monkeypatch.setenv('JORDAN_ADMIN_USERS', '[]')
    assert identity.load_operators() == {}


def test_declared_accounts_are_loaded(monkeypatch):
    declare(monkeypatch, account('alice'), account('bob', role=identity.ROLE_VIEWER))
    assert list(identity.load_operators()) == ['alice', 'bob']


# An account that quietly disappears locks its holder out, and nobody finds out
# before the day they try to log in. Every one of these stops the server instead.
@pytest.mark.parametrize('raw', [
    'alice:pwd',                                  # not JSON
    '{"login": "alice"}',                         # an object, not an array
    '["alice"]',                                  # an account that is not an object
])
def test_unusable_declaration_stops_the_server_from_starting(monkeypatch, raw):
    monkeypatch.setenv('JORDAN_ADMIN_USERS', raw)
    with pytest.raises(ConfigurationError):
        identity.load_operators()


def test_a_lone_account_pasted_without_brackets_says_so(monkeypatch):
    """The slip someone actually makes when declaring their first operator. The
    refusal has to name the fix, because the symptom is read on a deployment that
    never started — the previous one keeps serving and keeps logging that no
    account is declared, which points nowhere near the brackets."""
    monkeypatch.setenv('JORDAN_ADMIN_USERS', json.dumps(account('alice')))
    with pytest.raises(ConfigurationError) as refused:
        identity.load_operators()
    reason = str(refused.value)
    assert 'wrap it in brackets' in reason
    assert 'alice' in reason  # the account it is talking about


# ── The helper that produces those entries ────────────────────────────────────


def test_the_helper_prints_a_value_that_can_be_pasted_as_is(capsys, monkeypatch):
    """It used to print the bare object under 'add this to the JSON array',
    which for a first account is an array that does not exist yet. What it prints
    first must be a declaration that boots, so feed it straight back in."""
    assert identity._main(['admin_identity.py', 'alice', 'pwd', 'operator']) == 0
    printed = capsys.readouterr().out
    offered = printed[printed.index('['):printed.index(']') + 1]

    monkeypatch.setenv('JORDAN_ADMIN_USERS', offered)
    loaded = identity.load_operators()
    assert list(loaded) == ['alice']
    assert loaded['alice']['role'] == identity.ROLE_OPERATOR
    assert identity.authenticate('alice', 'pwd')['permissions'] == ['read', 'send']


def test_account_without_password_hash_stops_the_server(monkeypatch):
    declare(monkeypatch, {'login': 'alice', 'role': 'admin'}, account('bob'))
    with pytest.raises(ConfigurationError):
        identity.load_operators()


def test_account_without_login_stops_the_server(monkeypatch):
    declare(monkeypatch, {'passwordHash': 'x', 'role': 'admin'})
    with pytest.raises(ConfigurationError):
        identity.load_operators()


def test_unknown_role_stops_the_server(monkeypatch):
    """Silently dropping it would leave an operator convinced they have access."""
    declare(monkeypatch, account('alice', role='superuser'), account('bob'))
    with pytest.raises(ConfigurationError):
        identity.load_operators()


def test_a_login_declared_twice_stops_the_server(monkeypatch):
    """One of the two passwords and roles would apply, and nothing would say which."""
    declare(monkeypatch, account('alice', 'first-pwd', identity.ROLE_VIEWER),
            account('alice', 'second-pwd', identity.ROLE_ADMIN))
    with pytest.raises(ConfigurationError):
        identity.load_operators()


def test_role_defaults_to_viewer(monkeypatch):
    """Omitting the role is not a mistake — it grants the least of them."""
    declare(monkeypatch, {'login': 'alice',
                          'passwordHash': generate_password_hash('pwd', method=TEST_HASH_METHOD)})
    assert identity.authenticate('alice', 'pwd')['role'] == identity.ROLE_VIEWER


# ── Authentication ────────────────────────────────────────────────────────────


def test_authenticate_returns_the_identity(monkeypatch):
    declare(monkeypatch, account('alice', 'pwd', identity.ROLE_OPERATOR))
    assert identity.authenticate('alice', 'pwd') == {
        'login': 'alice', 'role': 'operator', 'permissions': ['read', 'send'],
    }


def test_authenticate_rejects_a_wrong_password(monkeypatch):
    declare(monkeypatch, account('alice', 'pwd'))
    assert identity.authenticate('alice', 'other') is None


def test_authenticate_rejects_an_unknown_login(monkeypatch):
    declare(monkeypatch, account('alice', 'pwd'))
    assert identity.authenticate('ghost', 'pwd') is None


@pytest.mark.parametrize('login,password', [(None, 'pwd'), ('alice', None), ('', ''), ('alice', '')])
def test_authenticate_rejects_empty_credentials(monkeypatch, login, password):
    declare(monkeypatch, account('alice', 'pwd'))
    assert identity.authenticate(login, password) is None


def test_hash_password_round_trip(monkeypatch):
    """Hashes produced by the helper (production KDF) are accepted at login."""
    declare(monkeypatch, {'login': 'alice',
                          'passwordHash': identity.hash_password('pwd'),
                          'role': identity.ROLE_VIEWER})
    assert identity.authenticate('alice', 'pwd')['login'] == 'alice'
    assert identity.authenticate('alice', 'pwb') is None


# ── Session TTL ───────────────────────────────────────────────────────────────


def test_session_ttl_defaults_to_twelve_hours(monkeypatch):
    monkeypatch.delenv('JORDAN_ADMIN_SESSION_TTL', raising=False)
    assert identity.session_ttl() == 12 * 60 * 60


def test_session_ttl_is_configurable(monkeypatch):
    monkeypatch.setenv('JORDAN_ADMIN_SESSION_TTL', '300')
    assert identity.session_ttl() == 300


@pytest.mark.parametrize('value', ['not-a-number', '0', '-1'])
def test_unusable_session_ttl_falls_back_to_the_default(monkeypatch, value):
    monkeypatch.setenv('JORDAN_ADMIN_SESSION_TTL', value)
    assert identity.session_ttl() == 12 * 60 * 60
