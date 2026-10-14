"""What GET /admin/{id} hands out (JRD-39).

The route returns the stored record unmarshalled, and a root task stores the
token of its client. Reading it would let a `viewer` act as that client on
/client/*, which no role grants. These tests run the real route and the real
storage function against the in-memory RedisJSON stand-in of test_unregister.
"""

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import rejson_interface as storage  # noqa: E402

from .test_unregister import ROOT_ID, SUB_TASK_ID, rj  # noqa: F401 — rj is a fixture

STATUS_ID = 54321
MESSAGE_ID = 4321


@pytest.fixture
def records(rj):  # noqa: F811
    rj.store[STATUS_ID] = {'statusId': STATUS_ID, 'type': 'general', 'status': 'Running fine',
                           'parentTask': {'taskId': ROOT_ID, 'name': 'trainer'}}
    rj.store[MESSAGE_ID] = {'messageId': MESSAGE_ID, 'state': 'SERVER_RECEIVED',
                            'parentTask': {'taskId': SUB_TASK_ID, 'name': 'epoch'}}
    return rj


def test_a_viewer_does_not_get_the_token_of_a_root_task(client, records, login):
    r = client.get(f'/jordan/admin/{ROOT_ID}', headers=login('vic'))
    assert r.status_code == 200
    root = r.get_json()
    assert 'authToken' not in root
    assert root['name'] == 'trainer'
    assert root['state'] == storage.CLIENT_STATE_REGISTERED
    assert root['tasks'] == [SUB_TASK_ID]
    # the record itself is untouched: the client still authenticates with it
    assert records.store[ROOT_ID]['authToken'] == 'root-token'


@pytest.mark.parametrize('record_id, field, value', [
    (SUB_TASK_ID, 'name', 'epoch'),
    (STATUS_ID, 'status', 'Running fine'),
    (MESSAGE_ID, 'state', 'SERVER_RECEIVED'),
])
def test_the_other_records_read_as_stored(client, records, login, record_id, field, value):
    r = client.get(f'/jordan/admin/{record_id}', headers=login('vic'))
    assert r.status_code == 200
    assert r.get_json() == records.store[record_id]
    assert r.get_json()[field] == value


def test_a_token_nested_in_a_record_is_stripped_too(records):
    """An older server copied the whole root into parentTask, token included."""
    records.store[STATUS_ID]['parentTask']['authToken'] = 'root-token'
    records.store[STATUS_ID]['history'] = [{'authToken': 'root-token', 'step': 1}]
    status = storage.generic_query(STATUS_ID)
    assert status['parentTask'] == {'taskId': ROOT_ID, 'name': 'trainer'}
    assert status['history'] == [{'step': 1}]


def test_an_unknown_id_still_answers_204(client, records, login):
    r = client.get('/jordan/admin/999999', headers=login('vic'))
    assert r.status_code == 204
