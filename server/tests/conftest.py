import json
import os
import time
import pytest
from unittest.mock import MagicMock, patch
from werkzeug.security import generate_password_hash

# Must be set before server modules are imported
os.environ.setdefault('REDIS_HOST', 'localhost')
os.environ.setdefault('REDIS_PORT', '6379')
os.environ.setdefault('REDIS_PASSWORD', 'test_password')

# Patch redis.Redis so the module-level connection in rejson_interface.py
# does not attempt a real TCP connection during tests.
_redis_patcher = patch('redis.Redis', return_value=MagicMock())
_redis_patcher.start()

from api import app  # noqa: E402

# ── constants shared across tests ────────────────────────────────────────────

TASK_ID = 42
TOKEN = 'deadbeefdeadbeefdeadbeefdeadbeef'
ADMIN_TOKEN = 'cafebabecafebabecafebabecafebabe'
STATUS_ID = 9999
MESSAGE_ID = 12345

_MOCK_CLIENT = {
    'clientId': TASK_ID,
    'name': 'Test Bot',
    'state': 'REGISTERED',
    'tasks': [],
}

_MOCK_STATUS = {
    'statusId': STATUS_ID,
    'type': 'general',
    'status': 'Running fine',
    'timestamp': int(time.time()),
    'parentTask': {'taskId': TASK_ID, 'name': 'root'},
}

_MOCK_MESSAGE = {
    'messageId': MESSAGE_ID,
    'author': 'test_author',
    'action': {'actionName': 'test_action'},
    'audit': [{'timestamp': int(time.time()), 'state': 'SERVER_RECEIVED'}],
}

# ── Flask test client ─────────────────────────────────────────────────────────


@pytest.fixture
def client():
    app.config['TESTING'] = True
    with app.test_client() as c:
        yield c


@pytest.fixture
def auth_headers():
    return {'Authorization': f'Bearer {TOKEN}'}


# ── Auth helpers ──────────────────────────────────────────────────────────────


@pytest.fixture
def allow_auth(monkeypatch):
    monkeypatch.setattr('api.validate_auth_token', lambda task_id, token: True)


@pytest.fixture
def deny_auth(monkeypatch):
    monkeypatch.setattr('api.validate_auth_token', lambda task_id, token: False)


@pytest.fixture
def admin_token(monkeypatch):
    """Configure the shared bootstrap admin token on the server side."""
    monkeypatch.setenv('JORDAN_ADMIN_TOKEN', ADMIN_TOKEN)
    return ADMIN_TOKEN


@pytest.fixture
def no_admin_auth(monkeypatch):
    """Server started with no admin credential at all: the namespace must fail closed."""
    monkeypatch.delenv('JORDAN_ADMIN_TOKEN', raising=False)
    monkeypatch.delenv('JORDAN_ADMIN_USERS', raising=False)


@pytest.fixture
def admin_headers(admin_token):
    return {'Authorization': f'Bearer {admin_token}'}


# ── Operator accounts and sessions ────────────────────────────────────────────

# Cheap KDF parameters: the production default (600k pbkdf2 rounds) would add
# ~0.3s per hash to the suite. hash_password() is covered separately.
_TEST_HASH_METHOD = 'pbkdf2:sha256:1000'

OPERATORS = {
    'vic': {'password': 'viewer-pwd', 'role': 'viewer'},
    'olga': {'password': 'operator-pwd', 'role': 'operator'},
    'ada': {'password': 'admin-pwd', 'role': 'admin'},
}


def _declared_operators():
    return json.dumps([
        {
            'login': login,
            'passwordHash': generate_password_hash(account['password'], method=_TEST_HASH_METHOD),
            'role': account['role'],
        }
        for login, account in OPERATORS.items()
    ])


@pytest.fixture
def admin_users(monkeypatch):
    """Declare the operator accounts of OPERATORS on the server side."""
    monkeypatch.delenv('JORDAN_ADMIN_TOKEN', raising=False)
    monkeypatch.setenv('JORDAN_ADMIN_USERS', _declared_operators())
    return OPERATORS


@pytest.fixture
def admin_sessions(monkeypatch):
    """In-memory stand-in for the Redis session store, so a test can log in and
    reuse the returned token."""
    sessions = {}
    monkeypatch.setattr(
        'api.store_admin_session',
        lambda token, operator, ttl: sessions.__setitem__(token, dict(operator)),
    )
    monkeypatch.setattr('api.read_admin_session', lambda token: sessions.get(token))
    monkeypatch.setattr(
        'api.delete_admin_session',
        lambda token: sessions.pop(token, None) is not None,
    )
    return sessions


@pytest.fixture
def login(client, admin_users, admin_sessions):
    """Log an operator in and return the headers carrying its session token."""
    def _login(operator_login):
        r = client.post(
            '/jordan/admin/login',
            json={'login': operator_login, 'password': OPERATORS[operator_login]['password']},
        )
        assert r.status_code == 200, r.get_json()
        return {'Authorization': f"Bearer {r.get_json()['token']}"}
    return _login


# ── Per-function interface mocks (data from mock.py patterns) ─────────────────


@pytest.fixture
def mock_register(monkeypatch):
    monkeypatch.setattr(
        'api.register_client',
        lambda payload: {'authToken': TOKEN, 'taskId': TASK_ID},
    )


@pytest.fixture
def mock_create_task(monkeypatch):
    monkeypatch.setattr(
        'api.create_task',
        lambda parent_task_id, payload: {'taskId': TASK_ID + 1},
    )


@pytest.fixture
def mock_post_status(monkeypatch):
    monkeypatch.setattr(
        'api.post_status',
        lambda task_id, payload: {'statusId': STATUS_ID},
    )


@pytest.fixture
def mock_read_message(monkeypatch):
    monkeypatch.setattr('api.read_message', lambda task_id: _MOCK_MESSAGE)
    return _MOCK_MESSAGE


@pytest.fixture
def mock_read_message_empty(monkeypatch):
    monkeypatch.setattr('api.read_message', lambda task_id: None)


@pytest.fixture
def mock_update_task(monkeypatch):
    valid = {'STARTED', 'RUNNING', 'PAUSED', 'COMPLETE', 'ERROR', 'TIME_OUT'}
    monkeypatch.setattr('api.update_task', lambda task_id, state: state in valid)


@pytest.fixture
def mock_update_message(monkeypatch):
    monkeypatch.setattr(
        'api.update_message',
        lambda task_id, message_id, state: True,
    )


@pytest.fixture
def mock_unregister(monkeypatch):
    monkeypatch.setattr('api.unregister', lambda client_id: True)


@pytest.fixture
def mock_list_clients(monkeypatch):
    monkeypatch.setattr('api.list_clients', lambda auth: [_MOCK_CLIENT])
    return [_MOCK_CLIENT]


@pytest.fixture
def mock_list_actions(monkeypatch):
    actions = [
        {
            'actionName': 'think',
            'parameters': [{'name': 'subject', 'type': 'string', 'mandatory': True}],
            'parentTask': {'taskId': TASK_ID, 'name': 'root'},
        }
    ]
    monkeypatch.setattr('api.list_actions', lambda task_id, auth: actions)
    return actions


@pytest.fixture
def mock_read_status(monkeypatch):
    monkeypatch.setattr('api.read_status', lambda task_id, count: [_MOCK_STATUS])
    return [_MOCK_STATUS]


@pytest.fixture
def mock_post_message(monkeypatch):
    monkeypatch.setattr('api.post_message', lambda task_id, payload: MESSAGE_ID)


@pytest.fixture
def captured_message(monkeypatch):
    """Payload as it reaches the storage layer, to check what the server rewrote."""
    captured = {}

    def _post_message(task_id, payload):
        captured.update(payload)
        return MESSAGE_ID

    monkeypatch.setattr('api.post_message', _post_message)
    return captured


@pytest.fixture
def mock_list_messages(monkeypatch):
    monkeypatch.setattr('api.list_messages', lambda task_id: [_MOCK_MESSAGE])
    return [_MOCK_MESSAGE]


@pytest.fixture
def mock_delete_task(monkeypatch):
    monkeypatch.setattr('api.delete_task', lambda task_id: True)


@pytest.fixture
def mock_delete_all(monkeypatch):
    monkeypatch.setattr('api.delete_all', lambda payload=None: True)


@pytest.fixture
def mock_generic_query(monkeypatch):
    monkeypatch.setattr('api.generic_query', lambda generic_id: _MOCK_STATUS)
    return _MOCK_STATUS
