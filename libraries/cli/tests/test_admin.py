import json

import responses as responses_lib
from typer.testing import CliRunner

from jordan_cli.admin import admin_app

runner = CliRunner()

BASE_URL = "http://testserver/jordan/"
SERVER_OPT = ["--server", BASE_URL]


def _url(path: str) -> str:
    return BASE_URL + path


# ── list ───────────────────────────────────────────────────────────────────────


class TestList:

    @responses_lib.activate
    def test_no_clients_prints_empty_message(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), json=[], status=200)
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
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "my-bot" in result.output
        assert "RUNNING" in result.output
        assert "subtask-a" in result.output
        assert "50%" in result.output

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/clients"), status=500)
        result = runner.invoke(admin_app, ["list"] + SERVER_OPT)
        assert result.exit_code == 1

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
        result = runner.invoke(admin_app, ["send", "1", "stop"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "42" in result.output

    @responses_lib.activate
    def test_params_build_correct_payload(self):
        responses_lib.add(responses_lib.POST, _url("admin/1/message"), json=1, status=201)
        runner.invoke(
            admin_app,
            ["send", "1", "shoot", "-p", "player=Jordan", "-p", "points=3"] + SERVER_OPT,
        )
        payload = json.loads(responses_lib.calls[0].request.body)
        assert payload["action"]["actionName"] == "shoot"
        assert payload["action"]["placeholders"] == {"player": "Jordan", "points": "3"}

    def test_invalid_param_format_exits_1(self):
        result = runner.invoke(admin_app, ["send", "1", "shoot", "-p", "badparam"] + SERVER_OPT)
        assert result.exit_code == 1
        assert "key=value" in result.output

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.POST, _url("admin/1/message"), status=400, body="Bad Request")
        result = runner.invoke(admin_app, ["send", "1", "stop"] + SERVER_OPT)
        assert result.exit_code == 1

    def test_no_server_url_exits_1(self):
        result = runner.invoke(admin_app, ["send", "1", "stop"])
        assert result.exit_code == 1


# ── message-status ─────────────────────────────────────────────────────────────


class TestMessageStatus:

    @responses_lib.activate
    def test_found_prints_json(self):
        data = {"messageId": 5, "state": "MESSAGE_PROCESSED"}
        responses_lib.add(responses_lib.GET, _url("admin/5"), json=data, status=200)
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 0
        assert "MESSAGE_PROCESSED" in result.output

    @responses_lib.activate
    def test_not_found_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/5"), status=204)
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 1

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.GET, _url("admin/5"), status=500)
        result = runner.invoke(admin_app, ["message-status", "5"] + SERVER_OPT)
        assert result.exit_code == 1

    def test_no_server_url_exits_1(self):
        result = runner.invoke(admin_app, ["message-status", "5"])
        assert result.exit_code == 1
