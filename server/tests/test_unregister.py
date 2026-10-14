"""What a token is worth once its client has unregistered (JRD-38).

The contract says unregistering ends the registration: no call from that client
is accepted any more. These tests run the real guard and the real storage
functions against an in-memory stand-in for RedisJSON, so that the token check
reads what `unregister` actually left behind.
"""

import copy

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import rejson_interface as storage  # noqa: E402

from .conftest import ADMIN_TOKEN

ROOT_ID = 123
SUB_TASK_ID = 456


class _FakeJson:
    """The few RedisJSON commands the code under test uses, on root paths
    (`.`) and on top-level fields (`.state`)."""

    def __init__(self, store):
        self.store = store

    def get(self, key, *path):
        doc = self.store.get(key)
        if doc is None or not path or path[0] == '.':
            return copy.deepcopy(doc)
        return copy.deepcopy(doc.get(path[0].lstrip('.')))

    def set(self, key, path, value):
        if path == '.':
            self.store[key] = copy.deepcopy(value)
        else:
            self.store[key][path.lstrip('.')] = copy.deepcopy(value)
        return True

    def delete(self, key, path):
        return int(self.store[key].pop(path.lstrip('.'), None) is not None)


class _FakePipeline:
    def __init__(self, json):
        self._json = json
        self._calls = []

    def json(self):
        pipeline = self

        class _Buffered:
            def __getattr__(self, name):
                return lambda *args: pipeline._calls.append((name, args))
        return _Buffered()

    def execute(self):
        return [getattr(self._json, name)(*args) for name, args in self._calls]


class _FakeRedis:
    def __init__(self):
        self.store = {}
        self._json = _FakeJson(self.store)

    def json(self):
        return self._json

    def pipeline(self):
        return _FakePipeline(self._json)


@pytest.fixture
def rj(monkeypatch):
    fake = _FakeRedis()
    fake.store[ROOT_ID] = {'taskId': ROOT_ID, 'clientId': ROOT_ID, 'name': 'trainer',
                           'authToken': 'root-token', 'state': storage.CLIENT_STATE_REGISTERED,
                           'tasks': [SUB_TASK_ID]}
    fake.store[SUB_TASK_ID] = {'taskId': SUB_TASK_ID, 'parentTaskId': ROOT_ID, 'name': 'epoch',
                               'state': storage.TASK_STATE_RUNNING, 'tasks': []}
    monkeypatch.setattr(storage, 'rj', fake)
    return fake


def _bearer(token):
    return {'Authorization': f'Bearer {token}'}


def _unregister(client):
    r = client.post(f'/jordan/client/{ROOT_ID}/unregister', headers=_bearer('root-token'))
    assert r.status_code == 200


@pytest.mark.parametrize('task_id', [ROOT_ID, SUB_TASK_ID])
def test_the_token_works_until_unregister(client, rj, task_id):
    r = client.put(f'/jordan/client/{task_id}/PAUSED', headers=_bearer('root-token'))
    assert r.status_code == 202


@pytest.mark.parametrize('task_id', [ROOT_ID, SUB_TASK_ID])
def test_the_token_is_refused_after_unregister(client, rj, task_id):
    _unregister(client)
    r = client.put(f'/jordan/client/{task_id}/PAUSED', headers=_bearer('root-token'))
    assert r.status_code == 401
    assert rj.store[task_id]['state'] != storage.TASK_STATE_PAUSED


def test_unregister_cannot_be_replayed(client, rj):
    _unregister(client)
    r = client.post(f'/jordan/client/{ROOT_ID}/unregister', headers=_bearer('root-token'))
    assert r.status_code == 401


def test_unregister_drops_the_token_and_keeps_the_record(client, rj):
    _unregister(client)
    root = rj.store[ROOT_ID]
    assert 'authToken' not in root
    assert root['state'] == storage.CLIENT_STATE_UNREGISTERED
    assert root['name'] == 'trainer' and root['tasks'] == [SUB_TASK_ID]
    assert SUB_TASK_ID in rj.store


def test_the_admin_side_still_reads_the_unregistered_task(client, rj, admin_token):
    _unregister(client)
    r = client.get(f'/jordan/admin/{ROOT_ID}', headers=_bearer(ADMIN_TOKEN))
    assert r.status_code == 200
    assert r.get_json()['state'] == storage.CLIENT_STATE_UNREGISTERED


def test_a_root_unregistered_before_the_fix_is_refused_too(rj):
    """A record unregistered by an older server still carries its token."""
    rj.store[ROOT_ID]['state'] = storage.CLIENT_STATE_UNREGISTERED
    assert not storage.validate_auth_token(ROOT_ID, 'root-token')
    assert not storage.validate_auth_token(SUB_TASK_ID, 'root-token')
