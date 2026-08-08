"""Tests for the Redis storage of admin sessions (rejson_interface).

`rj` is the MagicMock installed by conftest, so these check how Redis is driven,
not Redis itself.
"""

from hashlib import sha256

import pytest

import rejson_interface as store

TOKEN = 'a-session-token'
OPERATOR = {'login': 'ada', 'role': 'admin', 'permissions': ['read', 'send', 'delete']}


@pytest.fixture(autouse=True)
def reset_redis_mock():
    store.rj.reset_mock()


def expected_key():
    return 'admin_session_' + sha256(TOKEN.encode('utf-8')).hexdigest()


def test_session_key_never_contains_the_token():
    """A dump of the base must not hand out usable tokens."""
    key = store._admin_session_key(TOKEN)
    assert TOKEN not in key
    assert key == expected_key()


def test_store_admin_session_applies_the_ttl():
    """Without EXPIRE, a session token would live forever."""
    store.store_admin_session(TOKEN, OPERATOR, 60)
    pipeline = store.rj.pipeline.return_value
    pipeline.json.return_value.set.assert_called_once_with(expected_key(), '.', OPERATOR)
    pipeline.expire.assert_called_once_with(expected_key(), 60)
    pipeline.execute.assert_called_once()


def test_read_admin_session_looks_up_the_hashed_key():
    store.rj.json.return_value.get.return_value = OPERATOR
    assert store.read_admin_session(TOKEN) == OPERATOR
    store.rj.json.return_value.get.assert_called_once_with(expected_key())


def test_delete_admin_session_reports_whether_a_session_was_removed():
    store.rj.delete.return_value = 1
    assert store.delete_admin_session(TOKEN) is True
    store.rj.delete.assert_called_once_with(expected_key())

    store.rj.delete.return_value = 0
    assert store.delete_admin_session(TOKEN) is False
