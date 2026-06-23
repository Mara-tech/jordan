"""Integration tests for Jordan server endpoints.

Each test patches only the interface functions it needs (via conftest fixtures),
so no real Redis connection is required.
"""

from .conftest import TASK_ID, TOKEN, STATUS_ID, MESSAGE_ID


# ── Health / hello ────────────────────────────────────────────────────────────


def test_hello(client):
    r = client.get('/jordan/hello')
    assert r.status_code == 200


def test_admin_hello(client):
    r = client.get('/jordan/admin/hello')
    assert r.status_code == 200
    assert r.get_json()['test'] == 'success'


# ── Client: register ──────────────────────────────────────────────────────────


def test_register_returns_200(client, mock_register):
    r = client.post('/jordan/client/register', json={'name': 'TestBot'})
    assert r.status_code == 200


def test_register_response_shape(client, mock_register):
    r = client.post('/jordan/client/register', json={'name': 'TestBot'})
    data = r.get_json()
    assert 'authToken' in data
    assert 'taskId' in data
    assert data['taskId'] == TASK_ID


def test_register_without_body(client, mock_register):
    r = client.post('/jordan/client/register', json={})
    assert r.status_code == 200


# ── Client: auth enforcement ──────────────────────────────────────────────────


def test_no_auth_header_returns_401(client):
    r = client.post(f'/jordan/client/{TASK_ID}/task', json={'name': 'sub'})
    assert r.status_code == 401


def test_malformed_auth_header_returns_401(client):
    r = client.post(
        f'/jordan/client/{TASK_ID}/task',
        json={'name': 'sub'},
        headers={'Authorization': 'Token bad'},
    )
    assert r.status_code == 401


def test_wrong_token_returns_401(client, deny_auth, auth_headers):
    r = client.post(
        f'/jordan/client/{TASK_ID}/task',
        json={'name': 'sub'},
        headers=auth_headers,
    )
    assert r.status_code == 401


# ── Client: create task ───────────────────────────────────────────────────────


def test_create_task_returns_201(client, allow_auth, mock_create_task, auth_headers):
    r = client.post(
        f'/jordan/client/{TASK_ID}/task',
        json={'name': 'sub-task'},
        headers=auth_headers,
    )
    assert r.status_code == 201


def test_create_task_response_has_task_id(client, allow_auth, mock_create_task, auth_headers):
    r = client.post(
        f'/jordan/client/{TASK_ID}/task',
        json={'name': 'sub-task'},
        headers=auth_headers,
    )
    assert 'taskId' in r.get_json()


# ── Client: post status ───────────────────────────────────────────────────────


def test_post_status_returns_200(client, allow_auth, mock_post_status, auth_headers):
    payload = {'type': 'general', 'status': 'working', 'timestamp': 1000}
    r = client.post(
        f'/jordan/client/{TASK_ID}/status',
        json=payload,
        headers=auth_headers,
    )
    assert r.status_code == 200


def test_post_status_returns_status_id(client, allow_auth, mock_post_status, auth_headers):
    payload = {'type': 'general', 'status': 'working', 'timestamp': 1000}
    r = client.post(
        f'/jordan/client/{TASK_ID}/status',
        json=payload,
        headers=auth_headers,
    )
    assert r.get_json()['statusId'] == STATUS_ID


def test_post_status_no_auth_returns_401(client, auth_headers):
    payload = {'type': 'general', 'status': 'working', 'timestamp': 1000}
    r = client.post(f'/jordan/client/{TASK_ID}/status', json=payload)
    assert r.status_code == 401


# ── Client: read message ──────────────────────────────────────────────────────


def test_read_message_returns_200_when_message_present(
    client, allow_auth, mock_read_message, auth_headers
):
    r = client.get(f'/jordan/client/{TASK_ID}/message', headers=auth_headers)
    assert r.status_code == 200


def test_read_message_body_has_expected_fields(
    client, allow_auth, mock_read_message, auth_headers
):
    r = client.get(f'/jordan/client/{TASK_ID}/message', headers=auth_headers)
    data = r.get_json()
    assert data['messageId'] == MESSAGE_ID
    assert 'author' in data
    assert 'action' in data


def test_read_message_returns_204_when_empty(
    client, allow_auth, mock_read_message_empty, auth_headers
):
    r = client.get(f'/jordan/client/{TASK_ID}/message', headers=auth_headers)
    assert r.status_code == 204


# ── Client: update task state ─────────────────────────────────────────────────


def test_update_task_valid_state_returns_202(
    client, allow_auth, mock_update_task, auth_headers
):
    r = client.put(f'/jordan/client/{TASK_ID}/COMPLETE', headers=auth_headers)
    assert r.status_code == 202


def test_update_task_invalid_state_returns_400(
    client, allow_auth, mock_update_task, auth_headers
):
    r = client.put(f'/jordan/client/{TASK_ID}/BADSTATE', headers=auth_headers)
    assert r.status_code == 400


# ── Client: update message state ─────────────────────────────────────────────


def test_update_message_state_returns_202(
    client, allow_auth, mock_update_message, auth_headers
):
    r = client.put(
        f'/jordan/client/{TASK_ID}/{MESSAGE_ID}/MESSAGE_PROCESSED',
        headers=auth_headers,
    )
    assert r.status_code == 202


# ── Client: unregister ────────────────────────────────────────────────────────


def test_unregister_returns_200(client, allow_auth, mock_unregister, auth_headers):
    r = client.post(f'/jordan/client/{TASK_ID}/unregister', headers=auth_headers)
    assert r.status_code == 200


# ── Admin: list clients ───────────────────────────────────────────────────────


def test_list_clients_returns_200(client, mock_list_clients):
    r = client.get('/jordan/admin/clients')
    assert r.status_code == 200


def test_list_clients_returns_list(client, mock_list_clients):
    r = client.get('/jordan/admin/clients')
    data = r.get_json()
    assert isinstance(data, list)
    assert len(data) == 1
    assert data[0]['name'] == 'Test Bot'


# ── Admin: list actions ───────────────────────────────────────────────────────


def test_list_actions_returns_200(client, mock_list_actions):
    r = client.get(f'/jordan/admin/{TASK_ID}/actions')
    assert r.status_code == 200


def test_list_actions_returns_list(client, mock_list_actions):
    data = client.get(f'/jordan/admin/{TASK_ID}/actions').get_json()
    assert isinstance(data, list)
    assert data[0]['actionName'] == 'think'


# ── Admin: read status ────────────────────────────────────────────────────────


def test_read_status_returns_200(client, mock_read_status):
    r = client.get(f'/jordan/admin/{TASK_ID}/status/10')
    assert r.status_code == 200


def test_read_status_returns_list(client, mock_read_status):
    data = client.get(f'/jordan/admin/{TASK_ID}/status/10').get_json()
    assert isinstance(data, list)
    assert data[0]['statusId'] == STATUS_ID


# ── Admin: post message ───────────────────────────────────────────────────────


def test_post_message_returns_201(client, mock_post_message):
    payload = {'author': 'admin', 'action': {'actionName': 'think'}}
    r = client.post(f'/jordan/admin/{TASK_ID}/message', json=payload)
    assert r.status_code == 201


# ── Admin: list messages ──────────────────────────────────────────────────────


def test_list_messages_returns_200(client, mock_list_messages):
    r = client.get(f'/jordan/admin/{TASK_ID}/messages')
    assert r.status_code == 200


def test_list_messages_returns_list(client, mock_list_messages):
    data = client.get(f'/jordan/admin/{TASK_ID}/messages').get_json()
    assert isinstance(data, list)
    assert data[0]['messageId'] == MESSAGE_ID


# ── Admin: delete task ────────────────────────────────────────────────────────


def test_delete_task_returns_200(client, mock_delete_task):
    r = client.delete(f'/jordan/admin/{TASK_ID}')
    assert r.status_code == 200


# ── Admin: delete all ─────────────────────────────────────────────────────────


def test_delete_all_returns_200(client, mock_delete_all):
    r = client.delete('/jordan/admin/all')
    assert r.status_code == 200
