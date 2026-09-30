import json
from pathlib import Path

import pytest
import requests
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

    @responses_lib.activate
    def test_registration_key_is_sent_as_bearer_token(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        result = runner.invoke(
            app, ["register", "--server", BASE_URL, "--registration-key", "reg-key-789"]
        )
        assert result.exit_code == 0
        assert responses_lib.calls[0].request.headers["Authorization"] == "Bearer reg-key-789"

    @responses_lib.activate
    def test_registration_key_read_from_environment(self, monkeypatch):
        monkeypatch.setenv("JORDAN_REGISTRATION_KEY", "key-from-env")
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        result = runner.invoke(app, ["register", "--server", BASE_URL])
        assert result.exit_code == 0
        assert responses_lib.calls[0].request.headers["Authorization"] == "Bearer key-from-env"

    @responses_lib.activate
    def test_registration_key_is_not_written_to_the_session_file(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        runner.invoke(app, ["register", "--server", BASE_URL, "--registration-key", "reg-key-789"])
        assert "reg-key-789" not in Path(".jordan_session").read_text()


# ── missing session ────────────────────────────────────────────────────────────


class TestMissingSession:

    @pytest.mark.parametrize("cmd", [
        ["status", "hello"],
        ["progress", "50"],
        ["metric", "loss", "0.5"],
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
    @pytest.mark.parametrize("args", [["progress", "42%"], ["status", "42", "--type", "progress"]])
    def test_sends_an_integer(self, args):
        # the server moves the task's progress on a JSON integer only
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        result = runner.invoke(app, args)
        assert result.exit_code == 0
        assert json.loads(responses_lib.calls[0].request.body)["status"] == 42

    @responses_lib.activate
    @pytest.mark.parametrize("args", [["progress", "half"], ["progress", "150"], ["status", "half", "--type", "progress"]])
    def test_not_a_percentage_is_a_usage_error(self, args):
        _write_session()
        result = runner.invoke(app, args)
        assert result.exit_code == 2
        assert "from 0 to 100" in result.output
        assert len(responses_lib.calls) == 0

    @responses_lib.activate
    def test_server_error_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=500)
        result = runner.invoke(app, ["progress", "10"])
        assert result.exit_code == 1


# ── metric ─────────────────────────────────────────────────────────────────────


class TestMetric:

    def _sent(self, args):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "m"},
            status=200,
        )
        result = runner.invoke(app, ["metric", *args])
        return result, json.loads(responses_lib.calls[0].request.body)

    @responses_lib.activate
    def test_sends_name_value_and_step(self):
        result, payload = self._sent(["held-out loss", "0.6648", "--step", "3"])
        assert result.exit_code == 0
        assert result.output.strip() == "m"
        assert payload["type"] == "metric"
        assert payload["metric"] == {"name": "held-out loss", "value": 0.6648, "step": 3}
        assert payload["status"] == "held-out loss = 0.6648 (step 3)"

    @responses_lib.activate
    def test_step_is_optional(self):
        _, payload = self._sent(["throughput", "120"])
        assert payload["metric"] == {"name": "throughput", "value": 120}

    @responses_lib.activate
    def test_negative_value_after_double_dash(self):
        result, payload = self._sent(["--", "delta", "-0.5"])
        assert result.exit_code == 0
        assert payload["metric"]["value"] == -0.5

    @responses_lib.activate
    def test_targets_subtask_with_task_id(self):
        _write_session()
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{SUB_TASK_ID}/status"),
            json={"statusId": "m"},
            status=200,
        )
        result = runner.invoke(app, ["metric", "loss", "0.5", "--task-id", str(SUB_TASK_ID)])
        assert result.exit_code == 0

    @pytest.mark.parametrize("args", [["loss", "high"], ["loss", "0.5", "--step", "third"]])
    def test_not_a_number_exits_without_calling(self, args):
        _write_session()
        result = runner.invoke(app, ["metric", *args])
        assert result.exit_code != 0
        assert "not a number" in result.output

    @responses_lib.activate
    def test_server_error_exits_1(self):
        _write_session()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=400)
        result = runner.invoke(app, ["metric", "loss", "0.5"])
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

    def test_wait_returns_by_its_timeout_when_the_server_stops_answering(self, silent_server, invoke_within):
        # JRD-16: --timeout bounded the loop, not the reads inside it, so one read
        # held by a silent server held the command forever
        Path(".jordan_session").write_text(json.dumps(dict(SESSION, server=silent_server)))
        result, took = invoke_within(app, ["action", "--wait", "--timeout", "1", "--interval", "0"], seconds=10)
        assert result.exit_code == 1
        assert "Timeout: no action received." in result.output
        assert took < 3

    @responses_lib.activate
    def test_wait_polls_on_after_an_unanswered_read(self):
        _write_session()
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"), body=requests.exceptions.ReadTimeout()
        )
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
        assert json.loads(result.output)["actionName"] == "stop"

    @responses_lib.activate
    def test_wait_cuts_each_read_to_the_time_left(self):
        _write_session()
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        result = runner.invoke(
            app, ["action", "--wait", "--timeout", "1", "--interval", "0.3", "--request-timeout", "30"]
        )
        assert result.exit_code == 1
        timeouts = [call.request.req_kwargs["timeout"] for call in responses_lib.calls]
        assert len(timeouts) >= 2
        assert all(0 < t <= 1 for t in timeouts)
        assert timeouts == sorted(timeouts, reverse=True)

    @responses_lib.activate
    def test_wait_gives_each_request_the_request_timeout_while_time_is_left(self):
        _write_session()
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=MSG_PAYLOAD, status=200
        )
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/CLIENT_RECEIVED"),
            status=202,
        )
        result = runner.invoke(app, ["action", "--wait", "--timeout", "60", "--request-timeout", "5"])
        assert result.exit_code == 0
        # the read and its receipt
        assert [call.request.req_kwargs["timeout"] for call in responses_lib.calls] == [5, 5]

    # JRD-19: the read takes the action off the queue; an acknowledgement of receipt that
    # fails afterwards must not lose it — least of all under --wait, which read a timeout as
    # an empty queue and went on waiting for the action it had just consumed

    @staticmethod
    def _serve_a_message_whose_receipt(**receipt) -> None:
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=MSG_PAYLOAD, status=200
        )
        responses_lib.add(
            responses_lib.PUT, _url(f"client/{TASK_ID}/{MSG_ID}/CLIENT_RECEIVED"), **receipt
        )

    @pytest.mark.parametrize("args", [["action"], ["action", "--wait", "--timeout", "10", "--interval", "0"]])
    @pytest.mark.parametrize("receipt", [
        {"body": requests.exceptions.ReadTimeout("receipt unanswered")},
        {"body": requests.exceptions.ConnectionError("connection dropped")},
        {"status": 500},
    ], ids=["timeout", "connection-error", "refused"])
    @responses_lib.activate
    def test_prints_the_action_when_its_receipt_fails(self, args, receipt):
        _write_session()
        self._serve_a_message_whose_receipt(**receipt)
        result = runner.invoke(app, args)
        assert result.exit_code == 0
        assert json.loads(result.stdout)["actionName"] == "stop"
        assert f"did not record that action {MSG_ID} was received" in result.stderr
        # read once, acknowledged once: no second read waiting on an action already taken
        assert len(responses_lib.calls) == 2

    @responses_lib.activate
    def test_names_the_error_that_kept_the_receipt_from_the_server(self):
        _write_session()
        self._serve_a_message_whose_receipt(body=requests.exceptions.ReadTimeout("receipt unanswered"))
        result = runner.invoke(app, ["action"])
        assert "receipt unanswered" in result.stderr

    @responses_lib.activate
    def test_no_warning_when_the_receipt_is_recorded(self):
        _write_session()
        self._serve_a_message_whose_receipt(status=202)
        result = runner.invoke(app, ["action"])
        assert result.exit_code == 0
        assert result.stderr == ""


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


# ── request timeout ────────────────────────────────────────────────────────────


def _serve_every_endpoint() -> None:
    ok = {
        (responses_lib.POST, "client/register"): (200, {"taskId": TASK_ID, "authToken": AUTH_TOKEN}),
        (responses_lib.POST, f"client/{TASK_ID}/task"): (201, {"taskId": SUB_TASK_ID}),
        (responses_lib.POST, f"client/{TASK_ID}/status"): (200, {"statusId": "s-1"}),
        (responses_lib.GET, f"client/{TASK_ID}/message"): (200, MSG_PAYLOAD),
        (responses_lib.PUT, f"client/{TASK_ID}/{MSG_ID}/CLIENT_RECEIVED"): (202, None),
        (responses_lib.PUT, f"client/{TASK_ID}/COMPLETE"): (202, None),
        (responses_lib.PUT, f"client/{TASK_ID}/ERROR"): (202, None),
        (responses_lib.POST, f"client/{TASK_ID}/unregister"): (200, None),
    }
    for (method, path), (status, body) in ok.items():
        responses_lib.add(method, _url(path), json=body, status=status)


class TestRequestTimeout:
    """Every request the CLI sends is bounded: requests waits forever otherwise,
    on a server that accepts the connection and never answers (JRD-16)."""

    # each command, and how many requests it sends
    @pytest.mark.parametrize("cmd, requests_sent", [
        (["register", "--server", BASE_URL], 1),
        (["task-create", "child"], 1),
        (["status", "hello"], 1),
        (["progress", "50"], 1),
        (["metric", "loss", "0.5"], 1),
        (["action"], 2),
        (["complete"], 2),
        (["error", "boom"], 3),
        (["unregister"], 1),
    ])
    @responses_lib.activate
    def test_every_request_of_every_command_is_bounded(self, cmd, requests_sent):
        _write_session()
        _serve_every_endpoint()
        result = runner.invoke(app, cmd + ["--request-timeout", "7.5"])
        assert result.exit_code == 0, result.output
        assert [call.request.req_kwargs["timeout"] for call in responses_lib.calls] == [7.5] * requests_sent

    @responses_lib.activate
    def test_default_is_30_seconds(self):
        _write_session()
        _serve_every_endpoint()
        runner.invoke(app, ["status", "hello"])
        assert responses_lib.calls[0].request.req_kwargs["timeout"] == 30

    @responses_lib.activate
    def test_read_from_environment(self, monkeypatch):
        monkeypatch.setenv("JORDAN_REQUEST_TIMEOUT", "4")
        _write_session()
        _serve_every_endpoint()
        runner.invoke(app, ["status", "hello"])
        assert responses_lib.calls[0].request.req_kwargs["timeout"] == 4

    @pytest.mark.parametrize("value", ["0", "-1"])
    @responses_lib.activate
    def test_not_a_positive_number_is_a_usage_error(self, value):
        _write_session()
        result = runner.invoke(app, ["status", "hello", "--request-timeout", value])
        assert result.exit_code == 2
        assert len(responses_lib.calls) == 0

    def test_unanswered_request_exits_1_and_says_why(self, silent_server, invoke_within):
        Path(".jordan_session").write_text(json.dumps(dict(SESSION, server=silent_server)))
        result, _ = invoke_within(app, ["status", "hello", "--request-timeout", "0.5"], seconds=10)
        assert result.exit_code == 1
        assert "No answer from the server within 0.5 s" in result.output

    def test_unanswered_complete_keeps_the_session(self, silent_server, invoke_within):
        # the task is not known to be complete: the session stays, to try again
        Path(".jordan_session").write_text(json.dumps(dict(SESSION, server=silent_server)))
        result, _ = invoke_within(app, ["complete", "--request-timeout", "0.5"], seconds=10)
        assert result.exit_code == 1
        assert Path(".jordan_session").exists()

    def test_unanswered_register_writes_no_session(self, silent_server, invoke_within):
        result, _ = invoke_within(
            app, ["register", "--server", silent_server, "--request-timeout", "0.5"], seconds=10
        )
        assert result.exit_code == 1
        assert not Path(".jordan_session").exists()
