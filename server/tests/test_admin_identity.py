"""Unit tests for the operator accounts, roles and permissions of admin_identity."""

import json

import pytest
from werkzeug.security import generate_password_hash

import admin_identity as identity

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


def test_invalid_json_declares_no_account(monkeypatch):
    monkeypatch.setenv('JORDAN_ADMIN_USERS', 'alice:pwd')
    assert identity.load_operators() == {}


def test_json_object_instead_of_array_declares_no_account(monkeypatch):
    monkeypatch.setenv('JORDAN_ADMIN_USERS', '{"login": "alice"}')
    assert identity.load_operators() == {}


def test_entry_without_password_hash_is_skipped(monkeypatch):
    declare(monkeypatch, {'login': 'alice', 'role': 'admin'}, account('bob'))
    assert list(identity.load_operators()) == ['bob']


def test_entry_with_unknown_role_is_skipped(monkeypatch):
    declare(monkeypatch, account('alice', role='superuser'), account('bob'))
    assert list(identity.load_operators()) == ['bob']


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
