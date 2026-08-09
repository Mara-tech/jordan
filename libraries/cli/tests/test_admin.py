import json
import os
import stat
import sys
import time

import pytest
import responses as responses_lib
from typer.testing import CliRunner

from jordan_cli import admin
from jordan_cli.admin import admin_app

runner = CliRunner()

BASE_URL = "http://testserver/jordan/"
OTHER_URL = "http://elsewhere/jordan/"
SERVER_OPT = ["--server", BASE_URL]
TOKEN = "session-token-abc"

SESSION = {
    "token": TOKEN,
    "login": "bob",
    "role": "operator",
    "permissions": ["read", "send"],
}


def _url(path: str) -> str:
    return BASE_URL + path


@pytest.fixture(autouse=True)
def isolated_session(tmp_path, monkeypatch):
    """Keep every test off the real ~/.jordan_admin_session, and out of reach of
    the operator's own environment."""
    monkeypatch.setattr(admin, "SESSION_FILE", tmp_path / ".jordan_admin_session")
    for var in ("JORDAN_SERVER", admin.ADMIN_TOKEN_ENV_VAR, admin.ADMIN_PASSWORD_ENV_VAR):
        monkeypatch.delenv(var, raising=False)


def _open_session(base: str = BASE_URL, expires_in: int = 3600, token: str = TOKEN) -> None:
    admin._store_session(base, dict(SESSION, token=token, expiresAt=int(time.time()) + expires_in))


def _sent_token(call_index: int = 0) -> str:
    header = responses_lib.calls[call_index].request.headers.get("Authorization", "")
    return header[len("Bearer "):]


# ── login / logout / whoami ────────────────────────────────────────────────────


class TestLogin:

    @responses_lib.activate
    def test_stores_token_and_reports_role(self):
        responses_lib.add(
            responses_lib.POST,
            _url("admin/login"),
            json=dict(SESSION, expiresAt=int(time.time()) + 3600),
            status=200,
        )
        result = runner.invoke(
            admin_app, ["login", "--login", "bob", "--password", "s3cret"] + SERVER_OPT
        )
        assert result.exit_code == 0
        assert "bob" in result.output and "operator" in result.output
        assert admin._session_for(BASE_URL)["token"] == TOKEN

    @responses_lib.activate
    def test_password_is_prompted_when_not_given(self):
        responses_lib.add(
            responses_lib.POST,
            _url("admin/login"),
            json=dict(SESSION, expiresAt=int(time.time()) + 3600),
            status=200,
        )
        result = runner.invoke(admin_app, ["login", "--login", "bob"] + SERVER_OPT, input="s3cret\n")
        assert result.exit_code == 0
        assert json.loads(responses_lib.calls[0].request.body)["password"] == "s3cret"

    @responses_lib.activate
    def test_wrong_password_exits_1_and_stores_nothing(self):
        responses_lib.add(responses_lib.POST, _url("admin/login"), status=401)
        result = runner.invoke(
            admin_app, ["login", "--login", "bob", "--password", "wrong"] + SERVER_OPT
        )
        assert result.exit_code == 1
        assert "Invalid login or password" in result.output
        assert admin._session_for(BASE_URL) is None

    @responses_lib.activate
    def test_session_file_is_owner_only(self):
        if sys.platform == "win32":
            pytest.skip("POSIX file modes are not enforced on Windows")
        _open_session()
        assert stat.S_IMODE(os.stat(admin.SESSION_FILE).st_mode) == 0o600

    @responses_lib.activate
    def test_logged_in_server_becomes_the_default(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
        _open_session()
        result = runner.invoke(admin_app, ["list"])  # no --server
        assert result.exit_code == 0
        assert _sent_token() == TOKEN


class TestLogout:

    @responses_lib.activate
    def test_closes_session_and_drops_token(self):
        responses_lib.add(responses_lib.POST, _url("admin/logout"), status=200)
        _open_session()
        result = runner.invoke(admin_app, ["logout"] + SERVER_OPT)
        assert result.exit_code == 0
        assert _sent_token() == TOKEN
        assert admin._session_for(BASE_URL) is None

    @responses_lib.activate
    def test_refused_by_server_still_drops_the_local_token(self):
        responses_lib.add(responses_lib.POST, _url("admin/logout"), status=401)
        _open_session()
        result = runner.invoke(admin_app, ["logout"] + SERVER_OPT)
        assert result.exit_code == 1
        assert admin._session_for(BASE_URL) is None


class TestWhoami:

    @responses_lib.activate
    def test_prints_identity_and_permissions(self):
        responses_lib.add(responses_lib.GET, _url("admin/me"), json=SESSION, status=200)
        _open_session()
        result = runner.invoke(admin_app, ["whoami"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "bob" in result.output and "read, send" in result.output


# ── token resolution ───────────────────────────────────────────────────────────


class TestToken:

    @responses_lib.activate
    def test_option_is_sent_without_any_session(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
        result = runner.invoke(admin_app, ["list", "--token", "shared-token"] + SERVER_OPT)
        assert result.exit_code == 0
        assert _sent_token() == "shared-token"

    @responses_lib.activate
    def test_environment_variable_is_sent(self, monkeypatch):
        monkeypatch.setenv(admin.ADMIN_TOKEN_ENV_VAR, "env-token")
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 0
        assert _sent_token() == "env-token"

    @responses_lib.activate
    def test_option_wins_over_the_stored_session(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
        _open_session()
        runner.invoke(admin_app, ["list", "--token", "shared-token"] + SERVER_OPT)
        assert _sent_token() == "shared-token"

    def test_without_token_or_session_exits_1(self):
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "Not logged in" in result.output

    def test_session_is_not_sent_to_another_server(self):
        _open_session()
        result = runner.invoke(admin_app, ["list", "--server", OTHER_URL])
        assert result.exit_code == 1
        assert "Not logged in" in result.output

    def test_expired_session_is_refused(self):
        _open_session(expires_in=-1)
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "expired" in result.output


# ── list ───────────────────────────────────────────────────────────────────────


class TestList:

    @responses_lib.activate
    def test_no_clients_prints_empty_message(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
        _open_session()
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "No clients" in result.output

    @responses_lib.activate
    def test_shows_clients_and_tasks(self):
        clients = [
            {
                "clientId": 1,
                "name": "my-bot",
                "state": "RUNNING",
                "tasks": [
                    {"taskId": 10, "name": "subtask-a", "state": "RUNNING", "progress": "50%"}
                ],
            }
        ]
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=clients, status=200)
        _open_session()
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "my-bot" in result.output
        assert "RUNNING" in result.output
        assert "subtask-a" in result.output
        assert "50%" in result.output

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), status=500)
        _open_session()
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 1

    @responses_lib.activate
    def test_401_tells_the_operator_to_log_in(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), status=401)
        _open_session()
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "jordan-admin login" in result.output

    def test_no_server_url_exits_1(self):
        result = runner.invoke(admin_app, ["list"])
        assert result.exit_code == 1


# ── send ───────────────────────────────────────────────────────────────────────


class TestSend:

    @responses_lib.activate
    def test_success_prints_message_id(self):
        responses_lib.add(
            responses_lib.POST,
            _url("admin/1/message"),
            json=42,
            status=201,
        )
        _open_session()
        result = runner.invoke(admin_app, ["send", "1", "stop"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "42" in result.output
        assert _sent_token() == TOKEN

    @responses_lib.activate
    def test_params_build_correct_payload(self):
        responses_lib.add(responses_lib.POST, _url("admin/1/message"), json=1, status=201)
        _open_session()
        runner.invoke(
            admin_app,
            ["send", "1", "shoot", "-p", "player=Jordan", "-p", "points=3"] + SERVER_OPT,
        )
        payload = json.loads(responses_lib.calls[0].request.body)
        assert payload["action"]["actionName"] == "shoot"
        assert payload["action"]["placeholders"] == {"player": "Jordan", "points": "3"}
        # the server names the author from the token; claiming one here is noise
        assert "author" not in payload

    def test_invalid_param_format_exits_1(self):
        result = runner.invoke(admin_app, ["send", "1", "shoot", "-p", "badparam"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "key=value" in result.output

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.POST, _url("admin/1/message"), status=400, body="Bad Request")
        _open_session()
        result = runner.invoke(admin_app, ["send", "1", "stop"] + SERVER_OPT)
        assert result.exit_code == 1

    @responses_lib.activate
    def test_403_explains_the_role_is_not_allowed(self):
        responses_lib.add(responses_lib.POST, _url("admin/1/message"), status=403, body="viewer")
        _open_session()
        result = runner.invoke(admin_app, ["send", "1", "stop"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "not allowed" in result.output

    def test_no_server_url_exits_1(self):
        result = runner.invoke(admin_app, ["send", "1", "stop"])
        assert result.exit_code == 1


# ── watch ──────────────────────────────────────────────────────────────────────


class TestWatch:

    @responses_lib.activate
    def test_stops_on_401_instead_of_polling_on(self):
        responses_lib.add(responses_lib.GET, _url("admin/1/status/10"), status=401)
        _open_session()
        result = runner.invoke(admin_app, ["watch", "1"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "jordan-admin login" in result.output


# ── message-status ─────────────────────────────────────────────────────────────


class TestMessageStatus:

    @responses_lib.activate
    def test_found_prints_json(self):
        data = {"messageId": 5, "state": "MESSAGE_PROCESSED"}
        responses_lib.add(responses_lib.GET, _url("admin/5"), json=data, status=200)
        _open_session()
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "MESSAGE_PROCESSED" in result.output

    @responses_lib.activate
    def test_not_found_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/5"), status=204)
        _open_session()
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 1

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/5"), status=500)
        _open_session()
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 1

    def test_no_server_url_exits_1(self):
        result = runner.invoke(admin_app, ["message-status", "5"])
        assert result.exit_code == 1
