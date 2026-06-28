import os
import time
import pytest
from unittest.mock import MagicMock, patch

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
def mock_list_messages(monkeypatch):
    monkeypatch.setattr('api.list_messages', lambda task_id: [_MOCK_MESSAGE])
    return [_MOCK_MESSAGE]


@pytest.fixture
def mock_delete_task(monkeypatch):
    monkeypatch.setattr('api.delete_task', lambda task_id: True)


@pytest.fixture
def mock_delete_all(monkeypatch):
    monkeypatch.setattr('api.delete_all', lambda payload=None: True)
