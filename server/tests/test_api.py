"""Integration tests for Jordan server endpoints.

Each test patches only the interface functions it needs (via conftest fixtures),
so no real Redis connection is required.
"""

import pytest

from .conftest import TASK_ID, STATUS_ID, MESSAGE_ID, TOKEN, ADMIN_TOKEN


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


# ── Admin: auth enforcement ───────────────────────────────────────────────────

# Every guarded admin route, as (method, path). Kept in one place so a new
# endpoint added without _require_admin_auth() shows up as a failing test.
ADMIN_ROUTES = [
    ('get', '/jordan/admin/clients'),
    ('get', f'/jordan/admin/{TASK_ID}/actions'),
    ('get', f'/jordan/admin/{TASK_ID}/status/10'),
    ('post', f'/jordan/admin/{TASK_ID}/message'),
    ('get', f'/jordan/admin/{TASK_ID}/messages'),
    ('get', f'/jordan/admin/{MESSAGE_ID}'),
    ('delete', f'/jordan/admin/{TASK_ID}'),
    ('delete', '/jordan/admin/all'),
]


@pytest.mark.parametrize('method,path', ADMIN_ROUTES)
def test_admin_route_without_auth_returns_401(client, admin_token, method, path):
    r = getattr(client, method)(path)
    assert r.status_code == 401


@pytest.mark.parametrize('method,path', ADMIN_ROUTES)
def test_admin_route_with_wrong_token_returns_401(client, admin_token, method, path):
    r = getattr(client, method)(path, headers={'Authorization': 'Bearer wrong-token'})
    assert r.status_code == 401


def test_admin_malformed_auth_header_returns_401(client, admin_token):
    r = client.get('/jordan/admin/clients', headers={'Authorization': ADMIN_TOKEN})
    assert r.status_code == 401


def test_admin_rejects_a_client_token(client, admin_token, allow_auth, auth_headers):
    """A passive-client token must not open the admin namespace."""
    assert TOKEN != ADMIN_TOKEN
    r = client.get('/jordan/admin/clients', headers=auth_headers)
    assert r.status_code == 401


@pytest.mark.parametrize('method,path', ADMIN_ROUTES)
def test_admin_fails_closed_when_token_not_configured(client, no_admin_token, method, path):
    """No JORDAN_ADMIN_TOKEN on the server: the namespace is refused, not opened."""
    r = getattr(client, method)(path, headers={'Authorization': f'Bearer {ADMIN_TOKEN}'})
    assert r.status_code == 401


def test_admin_hello_stays_open(client, no_admin_token):
    """Health probe: no data, no auth."""
    r = client.get('/jordan/admin/hello')
    assert r.status_code == 200


# ── Admin: list clients ───────────────────────────────────────────────────────


def test_list_clients_returns_200(client, mock_list_clients, admin_headers):
    r = client.get('/jordan/admin/clients', headers=admin_headers)
    assert r.status_code == 200


def test_list_clients_returns_list(client, mock_list_clients, admin_headers):
    r = client.get('/jordan/admin/clients', headers=admin_headers)
    data = r.get_json()
    assert isinstance(data, list)
    assert len(data) == 1
    assert data[0]['name'] == 'Test Bot'


# ── Admin: list actions ───────────────────────────────────────────────────────


def test_list_actions_returns_200(client, mock_list_actions, admin_headers):
    r = client.get(f'/jordan/admin/{TASK_ID}/actions', headers=admin_headers)
    assert r.status_code == 200


def test_list_actions_returns_list(client, mock_list_actions, admin_headers):
    data = client.get(f'/jordan/admin/{TASK_ID}/actions', headers=admin_headers).get_json()
    assert isinstance(data, list)
    assert data[0]['actionName'] == 'think'


# ── Admin: read status ────────────────────────────────────────────────────────


def test_read_status_returns_200(client, mock_read_status, admin_headers):
    r = client.get(f'/jordan/admin/{TASK_ID}/status/10', headers=admin_headers)
    assert r.status_code == 200


def test_read_status_returns_list(client, mock_read_status, admin_headers):
    data = client.get(f'/jordan/admin/{TASK_ID}/status/10', headers=admin_headers).get_json()
    assert isinstance(data, list)
    assert data[0]['statusId'] == STATUS_ID


# ── Admin: post message ───────────────────────────────────────────────────────


def test_post_message_returns_201(client, mock_post_message, admin_headers):
    payload = {'author': 'admin', 'action': {'actionName': 'think'}}
    r = client.post(f'/jordan/admin/{TASK_ID}/message', json=payload, headers=admin_headers)
    assert r.status_code == 201


# ── Admin: list messages ──────────────────────────────────────────────────────


def test_list_messages_returns_200(client, mock_list_messages, admin_headers):
    r = client.get(f'/jordan/admin/{TASK_ID}/messages', headers=admin_headers)
    assert r.status_code == 200


def test_list_messages_returns_list(client, mock_list_messages, admin_headers):
    data = client.get(f'/jordan/admin/{TASK_ID}/messages', headers=admin_headers).get_json()
    assert isinstance(data, list)
    assert data[0]['messageId'] == MESSAGE_ID


# ── Admin: generic query ──────────────────────────────────────────────────────


def test_generic_query_returns_200(client, mock_generic_query, admin_headers):
    r = client.get(f'/jordan/admin/{MESSAGE_ID}', headers=admin_headers)
    assert r.status_code == 200


# ── Admin: delete task ────────────────────────────────────────────────────────


def test_delete_task_returns_200(client, mock_delete_task, admin_headers):
    r = client.delete(f'/jordan/admin/{TASK_ID}', headers=admin_headers)
    assert r.status_code == 200


# ── Admin: delete all ─────────────────────────────────────────────────────────


def test_delete_all_returns_200(client, mock_delete_all, admin_headers):
    r = client.delete('/jordan/admin/all', headers=admin_headers)
    assert r.status_code == 200
