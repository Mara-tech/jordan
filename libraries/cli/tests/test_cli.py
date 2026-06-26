import json
from pathlib import Path

import pytest
import responses as responses_lib
from typer.testing import CliRunner

from jordan_cli.cli import app

runner = CliRunner()

BASE_URL = "http://testserver/jordan/"
TASK_ID = "task-abc-123"
AUTH_TOKEN = "token-xyz-456"
MSG_ID = "msg-001"
SUB_TASK_ID = 999

SESSION = {
    "server": BASE_URL,
    "taskId": TASK_ID,
    "authToken": AUTH_TOKEN,
    "name": "test-client",
}

MSG_PAYLOAD = {
    "messageId": MSG_ID,
    "action": {"actionName": "stop", "placeholders": {}},
}


def _url(path: str) -> str:
    return BASE_URL + path


def _write_session() -> None:
    Path(".jordan_session").write_text(json.dumps(SESSION))


@pytest.fixture(autouse=True)
def in_tmp_dir(tmp_path, monkeypatch):
    monkeypatch.chdir(tmp_path)


# ── register ───────────────────────────────────────────────────────────────────


class TestRegister:

    @responses_lib.activate
    def test_success_creates_session_file(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        result = runner.invoke(app, ["register", "--server", BASE_URL, "--name", "my-bot"])
        assert result.exit_code == 0
        assert "my-bot" in result.output
        session = json.loads(Path(".jordan_session").read_text())
        assert session["taskId"] == TASK_ID
        assert session["authToken"] == AUTH_TOKEN

    @responses_lib.activate
    def test_server_error_exits_1(self):
        responses_lib.add(responses_lib.POST, _url("client/register"), status=401)
        result = runner.invoke(app, ["register", "--server", BASE_URL])
        assert result.exit_code == 1
        assert not Path(".jordan_session").exists()


# ── missing session ────────────────────────────────────────────────────────────


class TestMissingSession:

    @pytest.mark.parametrize("cmd", [
        ["status", "hello"],
        ["progress", "50"],
        ["action"],
        ["complete"],
        ["error"],
        ["unregister"],
        ["task-create", "my-task"],
    ])
    def test_exits_1_without_session(self, cmd):
        result = runner.invoke(app, cmd)
        assert result.exit_code == 1


# ── status ─────────────────────────────────────────────────────────────────────


class TestStatus:

    @responses_lib.activate
    def test_success_prints_status_id(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "status-001"},
            status=200,
        )
        result = runner.invoke(app, ["status", "Running..."])
        assert result.exit_code == 0
        assert "status-001" in result.output

    @responses_lib.activate
    def test_uses_provided_type(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        runner.invoke(app, ["status", "done", "--type", "success"])
        payload = json.loads(responses_lib.calls[0].request.body)
        assert payload["type"] == "success"

    @responses_lib.activate
    def test_targets_subtask_with_task_id(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{SUB_TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        result = runner.invoke(app, ["status", "Running", "--task-id", SUB_TASK_ID])
        assert result.exit_code == 0

    @responses_lib.activate
    def test_server_error_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=500)
        result = runner.invoke(app, ["status", "x"])
        assert result.exit_code == 1


# ── progress ───────────────────────────────────────────────────────────────────


class TestProgress:

    @responses_lib.activate
    def test_success(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        result = runner.invoke(app, ["progress", "42%"])
        assert result.exit_code == 0

    @responses_lib.activate
    def test_uses_progress_type(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        runner.invoke(app, ["progress", "50"])
        payload = json.loads(responses_lib.calls[0].request.body)
        assert payload["type"] == "progress"

    @responses_lib.activate
    def test_server_error_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=500)
        result = runner.invoke(app, ["progress", "10"])
        assert result.exit_code == 1


# ── action ─────────────────────────────────────────────────────────────────────


class TestAction:

    @responses_lib.activate
    def test_no_message_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        result = runner.invoke(app, ["action"])
        assert result.exit_code == 1

    @responses_lib.activate
    def test_prints_message_as_json(self):
        _write_session()
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=MSG_PAYLOAD, status=200
        )
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/CLIENT_RECEIVED"),
            status=202,
        )
        result = runner.invoke(app, ["action"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["messageId"] == MSG_ID
        assert data["actionName"] == "stop"

    @responses_lib.activate
    def test_wait_returns_message_on_second_poll(self):
        _write_session()
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=MSG_PAYLOAD, status=200
        )
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/CLIENT_RECEIVED"),
            status=202,
        )
        result = runner.invoke(app, ["action", "--wait", "--timeout", "10", "--interval", "0"])
        assert result.exit_code == 0
        data = json.loads(result.output)
        assert data["actionName"] == "stop"

    @responses_lib.activate
    def test_wait_timeout_exits_1(self):
        _write_session()
        # May or may not be polled depending on timing — handle both
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        result = runner.invoke(app, ["action", "--wait", "--timeout", "0", "--interval", "0"])
        assert result.exit_code == 1
        assert "Timeout" in result.output


# ── task-create ────────────────────────────────────────────────────────────────


class TestTaskCreate:

    @responses_lib.activate
    def test_success_prints_new_task_id(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/task"),
            json={"taskId": SUB_TASK_ID},
            status=201,
        )
        result = runner.invoke(app, ["task-create", "my-subtask"])
        assert result.exit_code == 0
        assert str(SUB_TASK_ID) in result.output

    @responses_lib.activate
    def test_under_explicit_parent(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{SUB_TASK_ID}/task"),
            json={"taskId": "sub-sub-001"},
            status=201,
        )
        result = runner.invoke(app, ["task-create", "child", "--task-id", SUB_TASK_ID])
        assert result.exit_code == 0
        assert "sub-sub-001" in result.output

    @responses_lib.activate
    def test_failure_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/task"), status=400)
        result = runner.invoke(app, ["task-create", "bad"])
        assert result.exit_code == 1


# ── complete ───────────────────────────────────────────────────────────────────


class TestComplete:

    @responses_lib.activate
    def test_root_task_unregisters_and_deletes_session(self):
        _write_session()
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/COMPLETE"), status=202)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        result = runner.invoke(app, ["complete"])
        assert result.exit_code == 0
        assert not Path(".jordan_session").exists()

    @responses_lib.activate
    def test_subtask_keeps_session_and_no_unregister(self):
        _write_session()
        responses_lib.add(responses_lib.PUT, _url(f"client/{SUB_TASK_ID}/COMPLETE"), status=202)
        result = runner.invoke(app, ["complete", "--task-id", SUB_TASK_ID])
        assert result.exit_code == 0
        assert Path(".jordan_session").exists()
        assert str(SUB_TASK_ID) in result.output
        # Only one HTTP call (COMPLETE), no unregister
        assert len(responses_lib.calls) == 1


# ── error ──────────────────────────────────────────────────────────────────────


class TestError:

    @responses_lib.activate
    def test_root_task_unregisters_and_deletes_session(self):
        _write_session()
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/ERROR"), status=202)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        result = runner.invoke(app, ["error"])
        assert result.exit_code == 0
        assert not Path(".jordan_session").exists()

    @responses_lib.activate
    def test_with_message_sends_failure_status_first(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/ERROR"), status=202)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        result = runner.invoke(app, ["error", "something went wrong"])
        assert result.exit_code == 0
        payload = json.loads(responses_lib.calls[0].request.body)
        assert payload["type"] == "failure"
        assert payload["status"] == "something went wrong"

    @responses_lib.activate
    def test_subtask_keeps_session_and_no_unregister(self):
        _write_session()
        responses_lib.add(responses_lib.PUT, _url(f"client/{SUB_TASK_ID}/ERROR"), status=202)
        result = runner.invoke(app, ["error", "--task-id", SUB_TASK_ID])
        assert result.exit_code == 0
        assert Path(".jordan_session").exists()
        assert len(responses_lib.calls) == 1


# ── unregister ─────────────────────────────────────────────────────────────────


class TestUnregister:

    @responses_lib.activate
    def test_success_deletes_session(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        result = runner.invoke(app, ["unregister"])
        assert result.exit_code == 0
        assert not Path(".jordan_session").exists()

    @responses_lib.activate
    def test_server_error_exits_1_and_keeps_session(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=500)
        result = runner.invoke(app, ["unregister"])
        assert result.exit_code == 1
        assert Path(".jordan_session").exists()
