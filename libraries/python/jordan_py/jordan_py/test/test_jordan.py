import json
import logging
import os
import threading
import unittest

import requests
import responses as responses_lib

from jordan_py import jordan

BASE_URL = "http://testserver/jordan/"
TASK_ID = "task-abc-123"
AUTH_TOKEN = "token-xyz-456"
MSG_ID = "msg-001"


def _url(path: str) -> str:
    return BASE_URL + path


class TestActionBuilder(unittest.TestCase):

    def test_action_builder(self):
        actions = (jordan
                   .with_action('shoot')
                   .with_parameter('player_name', jordan.PARAMETER_TYPE_STRING, default_value='Jordan')
                   .with_parameter('points', jordan.PARAMETER_TYPE_INT)
                   .build())
        self.assertIsNotNone(actions)
        self.assertEqual(len(actions), 1)
        self.assertEqual(actions[0]['actionName'], 'shoot')
        params = actions[0]['parameters']
        self.assertEqual(len(params), 2)
        self.assertEqual(params[0]['name'], 'player_name')
        self.assertEqual(params[0]['type'], 'string')
        self.assertEqual(params[0].get('defaultValue'), 'Jordan')
        self.assertEqual(params[1]['name'], 'points')
        self.assertEqual(params[1]['type'], 'int')
        self.assertIsNone(params[1].get('defaultValue'))

    def test_invalid_parameter_type_raises(self):
        with self.assertRaises(ValueError):
            jordan.with_action('shoot').with_parameter('x', 'boolean')

    def test_multiple_actions(self):
        actions = (jordan.with_action('start').with_action('stop').build())
        self.assertEqual(len(actions), 2)
        names = [a['actionName'] for a in actions]
        self.assertIn('start', names)
        self.assertIn('stop', names)


class TestRegister(unittest.TestCase):

    @responses_lib.activate
    def test_register_success(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        instance = jordan.register(BASE_URL, "test-client")
        self.assertIsNotNone(instance)
        self.assertEqual(instance.task_id, TASK_ID)
        self.assertEqual(instance.auth_token, AUTH_TOKEN)
        self.assertEqual(instance.instance_name, "test-client")

    @responses_lib.activate
    def test_register_failure_returns_none(self):
        responses_lib.add(responses_lib.POST, _url("client/register"), status=401)
        self.assertIsNone(jordan.register(BASE_URL, "test-client"))

    @responses_lib.activate
    def test_register_sends_actions_in_payload(self):
        actions = jordan.with_action("stop").build()
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        jordan.register(BASE_URL, actions=actions)
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertIn("actions", payload)
        self.assertEqual(payload["actions"][0]["actionName"], "stop")

    @responses_lib.activate
    def test_register_sends_password_in_payload(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )
        jordan.register(BASE_URL, password="secret")
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertEqual(payload["password"], "secret")


class TestRegistrationKey(unittest.TestCase):
    """Servers that closed registration expect the key as a bearer token."""

    REGISTRATION_KEY = "reg-key-789"

    def setUp(self):
        self._saved_key = os.environ.pop(jordan.REGISTRATION_KEY_ENV_VAR, None)

    def tearDown(self):
        if self._saved_key is not None:
            os.environ[jordan.REGISTRATION_KEY_ENV_VAR] = self._saved_key
        else:
            os.environ.pop(jordan.REGISTRATION_KEY_ENV_VAR, None)

    def _stub_register(self):
        responses_lib.add(
            responses_lib.POST,
            _url("client/register"),
            json={"taskId": TASK_ID, "authToken": AUTH_TOKEN},
            status=200,
        )

    @responses_lib.activate
    def test_no_key_sends_no_authorization_header(self):
        self._stub_register()
        jordan.register(BASE_URL)
        self.assertNotIn("Authorization", responses_lib.calls[0].request.headers)

    @responses_lib.activate
    def test_key_is_sent_as_bearer_token(self):
        self._stub_register()
        jordan.register(BASE_URL, registration_key=self.REGISTRATION_KEY)
        self.assertEqual(
            responses_lib.calls[0].request.headers["Authorization"],
            f"Bearer {self.REGISTRATION_KEY}",
        )

    @responses_lib.activate
    def test_key_never_reaches_the_payload(self):
        self._stub_register()
        jordan.register(BASE_URL, registration_key=self.REGISTRATION_KEY)
        self.assertNotIn(self.REGISTRATION_KEY, json.loads(responses_lib.calls[0].request.body).values())

    @responses_lib.activate
    def test_key_falls_back_to_the_environment(self):
        self._stub_register()
        os.environ[jordan.REGISTRATION_KEY_ENV_VAR] = "key-from-env"
        jordan.register(BASE_URL)
        self.assertEqual(
            responses_lib.calls[0].request.headers["Authorization"], "Bearer key-from-env"
        )

    @responses_lib.activate
    def test_argument_wins_over_the_environment(self):
        self._stub_register()
        os.environ[jordan.REGISTRATION_KEY_ENV_VAR] = "key-from-env"
        jordan.register(BASE_URL, registration_key=self.REGISTRATION_KEY)
        self.assertEqual(
            responses_lib.calls[0].request.headers["Authorization"],
            f"Bearer {self.REGISTRATION_KEY}",
        )


class TestJordanInstance(unittest.TestCase):

    def _make_instance(self) -> jordan.JordanInstance:
        return jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client")

    @responses_lib.activate
    def test_send_status_returns_status_id(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "status-001"},
            status=200,
        )
        self.assertEqual(self._make_instance().send_status("Running..."), "status-001")

    @responses_lib.activate
    def test_send_progress_uses_progress_type(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "status-002"},
            status=200,
        )
        self._make_instance().send_progress(50)
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertEqual(payload["type"], jordan.PROGRESS_STATUS_TYPE)

    def _sent_progress(self, value, send=None):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "status-002"},
            status=200,
        )
        (send or self._make_instance().send_progress)(value)
        return json.loads(responses_lib.calls[-1].request.body)["status"]

    @responses_lib.activate
    def test_send_progress_sends_an_integer_percentage(self):
        # the server moves the task's progress on a JSON integer only
        for value, expected in [(42, 42), (0, 0), (100, 100), (42.9, 42), (99.99, 99),
                                ("42", 42), (" 42 % ", 42), ("42.5%", 42)]:
            with self.subTest(value=value):
                sent = self._sent_progress(value)
                self.assertEqual(sent, expected)
                self.assertIs(type(sent), int)

    @responses_lib.activate
    def test_send_typed_status_converts_a_progress_too(self):
        instance = self._make_instance()
        sent = self._sent_progress("75%", send=lambda v: instance.send_status(v, status_type=jordan.PROGRESS_STATUS_TYPE))
        self.assertEqual(sent, 75)

    @responses_lib.activate
    def test_send_progress_refuses_what_is_not_a_percentage(self):
        for value in ["50% done", "", "half", -1, 100.5, float("nan"), float("inf"), True, None]:
            with self.subTest(value=value):
                with self.assertRaises(ValueError):
                    self._make_instance().send_progress(value)
        self.assertEqual(len(responses_lib.calls), 0)

    @responses_lib.activate
    def test_send_progress_refuses_before_starting_an_async_call(self):
        with self.assertRaises(ValueError):
            self._make_instance().send_progress("half", async_call=True)
        self.assertEqual(len(responses_lib.calls), 0)

    @responses_lib.activate
    def test_other_status_types_are_sent_as_given(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        self._make_instance().send_status("42%")
        self.assertEqual(json.loads(responses_lib.calls[0].request.body)["status"], "42%")

    @responses_lib.activate
    def test_send_success_status_uses_success_type(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        self._make_instance().send_success_status("done")
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertEqual(payload["type"], jordan.SUCCESS_STATUS_TYPE)

    @responses_lib.activate
    def test_send_failure_status_uses_failure_type(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "s"},
            status=200,
        )
        self._make_instance().send_failure_status("crashed")
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertEqual(payload["type"], jordan.FAILURE_STATUS_TYPE)

    def _sent_metric(self, *args, **kwargs):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/status"),
            json={"statusId": "metric-001"},
            status=200,
        )
        status_id = self._make_instance().send_metric(*args, **kwargs)
        return status_id, json.loads(responses_lib.calls[0].request.body)

    @responses_lib.activate
    def test_send_metric_carries_name_value_and_step(self):
        status_id, payload = self._sent_metric("held-out loss", 0.6648, step=3)
        self.assertEqual(status_id, "metric-001")
        self.assertEqual(payload["type"], jordan.METRIC_STATUS_TYPE)
        self.assertEqual(payload["metric"], {"name": "held-out loss", "value": 0.6648, "step": 3})
        self.assertIsInstance(payload["timestamp"], int)

    @responses_lib.activate
    def test_send_metric_reads_as_a_log_line(self):
        """A client that knows nothing of metrics shows the value as text."""
        _, payload = self._sent_metric("held-out loss", 0.6648, step=3)
        self.assertEqual(payload["status"], "held-out loss = 0.6648 (step 3)")

    @responses_lib.activate
    def test_send_metric_without_step(self):
        _, payload = self._sent_metric("throughput", 120)
        self.assertEqual(payload["metric"], {"name": "throughput", "value": 120})
        self.assertEqual(payload["status"], "throughput = 120")

    @responses_lib.activate
    def test_send_metric_step_zero_is_sent(self):
        _, payload = self._sent_metric("loss", 0.7, step=0)
        self.assertEqual(payload["metric"]["step"], 0)

    @responses_lib.activate
    def test_send_metric_accepts_scalars_of_numeric_libraries(self):
        """numpy.float32 or a torch scalar: not a float to the JSON encoder."""
        class Scalar:
            def __init__(self, value):
                self.value = value

            def __float__(self):
                return self.value

        _, payload = self._sent_metric("loss", Scalar(0.25), step=Scalar(4.0))
        self.assertEqual(payload["metric"], {"name": "loss", "value": 0.25, "step": 4.0})

    @responses_lib.activate
    def test_send_metric_refused_returns_none(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=400)
        self.assertIsNone(self._make_instance().send_metric("", 0.5))

    @responses_lib.activate
    def test_send_metric_skips_what_no_curve_can_hold(self):
        """A diverging training reports NaN: the loop sending it must not raise."""
        instance = self._make_instance()
        for value, step in ((float("nan"), None), (float("inf"), 3), (0.5, float("nan"))):
            self.assertIsNone(instance.send_metric("loss", value, step=step))
        self.assertEqual(len(responses_lib.calls), 0)

    @responses_lib.activate
    def test_send_status_failure_returns_none(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), status=500)
        self.assertIsNone(self._make_instance().send_status("x"))

    @responses_lib.activate
    def test_unregister_success(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        self.assertTrue(self._make_instance().unregister())

    @responses_lib.activate
    def test_unregister_failure(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=500)
        self.assertFalse(self._make_instance().unregister())

    @responses_lib.activate
    def test_complete_sends_complete_state(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{jordan.TASK_STATE_COMPLETE}"),
            status=202,
        )
        self.assertTrue(self._make_instance().complete())

    @responses_lib.activate
    def test_create_task_returns_task_instance(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/task"),
            json={"taskId": "sub-task-999"},
            status=201,
        )
        task = self._make_instance().create_task("my-subtask")
        self.assertIsNotNone(task)
        self.assertEqual(task.task_id, "sub-task-999")
        self.assertIsInstance(task, jordan.JordanTaskInstance)

    @responses_lib.activate
    def test_create_task_failure_returns_none(self):
        responses_lib.add(
            responses_lib.POST,
            _url(f"client/{TASK_ID}/task"),
            status=400,
        )
        self.assertIsNone(self._make_instance().create_task("my-subtask"))

    @responses_lib.activate
    def test_read_message_triggers_client_received(self):
        msg_payload = {
            "messageId": MSG_ID,
            "action": {"actionName": "stop", "placeholders": {}},
        }
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=msg_payload, status=200)
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_CLIENT_RECEIVED}"),
            status=202,
        )
        msg = self._make_instance().read_message()
        self.assertIsNotNone(msg)
        self.assertEqual(msg.action_name, "stop")
        self.assertEqual(len(responses_lib.calls), 2)
        self.assertIn(jordan.MESSAGE_CLIENT_RECEIVED, responses_lib.calls[1].request.url)

    @responses_lib.activate
    def test_read_message_returns_none_when_no_message(self):
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        self.assertIsNone(self._make_instance().read_message())


class TestReceiptAtBestEffort(unittest.TestCase):
    """The server takes a message off the queue when it answers the read: a message whose
    acknowledgement of receipt fails afterwards is still returned, or nobody ever gets it
    (JRD-19)."""

    RECEIPT_URL = _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_CLIENT_RECEIVED}")

    def _make_instance(self) -> jordan.JordanInstance:
        return jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client")

    def _serve_a_message(self, **receipt) -> None:
        msg_payload = {"messageId": MSG_ID, "action": {"actionName": "stop", "placeholders": {}}}
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=msg_payload, status=200)
        responses_lib.add(responses_lib.PUT, self.RECEIPT_URL, **receipt)

    @responses_lib.activate
    def test_a_confirmed_receipt_is_reported(self):
        self._serve_a_message(status=202)
        msg = self._make_instance().read_message()
        self.assertTrue(msg.receipt_confirmed)
        self.assertIsNone(msg.receipt_error)

    @responses_lib.activate
    def test_message_is_returned_when_its_receipt_times_out(self):
        self._serve_a_message(body=requests.exceptions.ReadTimeout("receipt unanswered"))
        msg = self._make_instance().read_message(timeout=1)
        self.assertEqual(msg.action_name, "stop")
        self.assertFalse(msg.receipt_confirmed)
        self.assertIsInstance(msg.receipt_error, requests.exceptions.Timeout)

    @responses_lib.activate
    def test_message_is_returned_when_its_receipt_cannot_connect(self):
        self._serve_a_message(body=requests.exceptions.ConnectionError("connection dropped"))
        msg = self._make_instance().read_message()
        self.assertEqual(msg.action_name, "stop")
        self.assertFalse(msg.receipt_confirmed)
        self.assertIsInstance(msg.receipt_error, requests.exceptions.ConnectionError)

    @responses_lib.activate
    def test_message_is_returned_when_its_receipt_is_refused(self):
        self._serve_a_message(status=500)
        msg = self._make_instance().read_message()
        self.assertEqual(msg.action_name, "stop")
        self.assertFalse(msg.receipt_confirmed)
        self.assertIsNone(msg.receipt_error)

    @responses_lib.activate
    def test_async_callback_gets_the_message_when_its_receipt_times_out(self):
        self._serve_a_message(body=requests.exceptions.ReadTimeout("receipt unanswered"))
        delivered = []
        done = threading.Event()
        self._make_instance().read_message(
            async_callback=lambda msg: (delivered.append(msg), done.set()), timeout=1
        )
        self.assertTrue(done.wait(5))
        self.assertEqual(delivered[0].action_name, "stop")
        self.assertIsNotNone(delivered[0].receipt_error)

    @responses_lib.activate
    def test_receipt_can_be_sent_again(self):
        self._serve_a_message(body=requests.exceptions.ReadTimeout("receipt unanswered"))
        msg = self._make_instance().read_message()
        responses_lib.replace(responses_lib.PUT, self.RECEIPT_URL, status=202)
        self.assertTrue(msg.received())
        self.assertTrue(msg.receipt_confirmed)
        self.assertIsNone(msg.receipt_error)

    @responses_lib.activate
    def test_a_retry_that_times_out_does_not_raise_either(self):
        # the retry the docs recommend is made while the program holds the message
        self._serve_a_message(status=500)
        msg = self._make_instance().read_message()
        responses_lib.replace(
            responses_lib.PUT, self.RECEIPT_URL, body=requests.exceptions.ReadTimeout("retry unanswered")
        )
        self.assertFalse(msg.received(timeout=1))
        self.assertFalse(msg.receipt_confirmed)
        self.assertIn("retry unanswered", str(msg.receipt_error))

    @responses_lib.activate
    def test_receipt_error_describes_the_last_attempt(self):
        self._serve_a_message(body=requests.exceptions.ReadTimeout("receipt unanswered"))
        msg = self._make_instance().read_message()
        responses_lib.replace(responses_lib.PUT, self.RECEIPT_URL, status=500)
        self.assertFalse(msg.received())
        self.assertFalse(msg.receipt_confirmed)
        self.assertIsNone(msg.receipt_error)

    @responses_lib.activate
    def test_receipt_can_be_left_to_the_caller(self):
        self._serve_a_message(status=202)
        msg = self._make_instance().read_message(send_receipt=False, timeout=1)
        self.assertEqual(len(responses_lib.calls), 1)
        self.assertFalse(msg.receipt_confirmed)
        self.assertTrue(msg.received(timeout=7))
        self.assertEqual(responses_lib.calls[1].request.req_kwargs["timeout"], 7)

    @responses_lib.activate
    def test_async_read_can_leave_the_receipt_to_the_caller(self):
        self._serve_a_message(status=202)
        done = threading.Event()
        self._make_instance().read_message(async_callback=lambda _msg: done.set(), send_receipt=False)
        self.assertTrue(done.wait(5))
        self.assertEqual(len(responses_lib.calls), 1)

    @responses_lib.activate
    def test_a_read_that_times_out_still_raises(self):
        # nothing was handed out, so nothing is lost: the caller decides whether to read again
        responses_lib.add(
            responses_lib.GET, _url(f"client/{TASK_ID}/message"),
            body=requests.exceptions.ReadTimeout("read unanswered"),
        )
        with self.assertRaises(requests.exceptions.Timeout):
            self._make_instance().read_message(timeout=1)


class TestUndecodableMessage(unittest.TestCase):
    """A message the server handed out is off its queue: one the library cannot decode is
    raised with its raw body and logged first, never dropped without a trace (JRD-29)."""

    MESSAGE_URL = _url(f"client/{TASK_ID}/message")

    def _make_instance(self) -> jordan.JordanInstance:
        return jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client")

    def _serve(self, body: str) -> None:
        responses_lib.add(responses_lib.GET, self.MESSAGE_URL, body=body, status=200)
        responses_lib.add(
            responses_lib.PUT, _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_CLIENT_RECEIVED}"), status=202
        )

    @responses_lib.activate
    def test_an_action_without_placeholders_has_no_parameter(self):
        for action in ({"actionName": "stop"}, {"actionName": "stop", "placeholders": None}):
            with self.subTest(action=action):
                responses_lib.reset()
                self._serve(json.dumps({"messageId": MSG_ID, "action": action}))
                msg = self._make_instance().read_message()
                self.assertEqual(msg.action_name, "stop")
                self.assertFalse(msg.placeholders.has_key("anything"))
                self.assertTrue(msg.receipt_confirmed)

    @responses_lib.activate
    def test_a_body_cut_short_is_raised_with_its_raw_content(self):
        body = '{"messageId": "msg-001", "action": {"actionName": "sto'
        self._serve(body)
        with self.assertLogs("jordan_py.jordan", level="ERROR") as logs:
            with self.assertRaises(jordan.UndecodableMessageError) as raised:
                self._make_instance().read_message()
        self.assertEqual(raised.exception.body, body)
        self.assertEqual(raised.exception.status_code, 200)
        self.assertIsInstance(raised.exception.__cause__, json.JSONDecodeError)
        self.assertIn(body, logs.output[0])
        # nothing to acknowledge: the library holds no message id
        self.assertEqual(len(responses_lib.calls), 1)

    @responses_lib.activate
    def test_it_stays_a_value_error(self):
        # what a caller catching the former JSONDecodeError already catches
        self._serve("<html>Bad gateway</html>")
        with self.assertLogs("jordan_py.jordan", level="ERROR"):
            with self.assertRaises(ValueError):
                self._make_instance().read_message()

    @responses_lib.activate
    def test_a_message_missing_a_field_is_raised_with_its_raw_content(self):
        for payload in ({"action": {"actionName": "stop"}}, {"messageId": MSG_ID}, ["not", "a", "message"],
                        {"messageId": MSG_ID, "action": {"actionName": "stop", "placeholders": ["x"]}}):
            with self.subTest(payload=payload):
                responses_lib.reset()
                body = json.dumps(payload)
                self._serve(body)
                with self.assertLogs("jordan_py.jordan", level="ERROR") as logs:
                    with self.assertRaises(jordan.UndecodableMessageError) as raised:
                        self._make_instance().read_message()
                self.assertEqual(raised.exception.body, body)
                self.assertIn(body, logs.output[0])

    @responses_lib.activate
    def test_the_async_path_logs_the_raw_body(self):
        # no caller is there to catch the exception: the log is the trace left
        body = '{"messageId": "msg-001", "act'
        self._serve(body)
        logged = threading.Event()
        records = []

        class _Capture(logging.Handler):
            def emit(self, record):
                records.append(record.getMessage())
                logged.set()

        logger = logging.getLogger("jordan_py.jordan")
        handler = _Capture(level=logging.ERROR)
        logger.addHandler(handler)
        raised = []
        thread_ended = threading.Event()
        previous_hook = threading.excepthook
        # the thread dies on the exception, as it would in a program: keep its traceback quiet
        threading.excepthook = lambda args: (raised.append(args.exc_type), thread_ended.set())
        try:
            delivered = []
            self._make_instance().read_message(async_callback=delivered.append)
            self.assertTrue(logged.wait(5))
            self.assertTrue(thread_ended.wait(5))
        finally:
            logger.removeHandler(handler)
            threading.excepthook = previous_hook
        self.assertIn(body, records[0])
        self.assertEqual(raised, [jordan.UndecodableMessageError])
        self.assertEqual(delivered, [])


class TestContextManager(unittest.TestCase):

    @responses_lib.activate
    def test_context_manager_calls_unregister_on_exit(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        with jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client"):
            pass
        self.assertEqual(len(responses_lib.calls), 1)
        self.assertIn("unregister", responses_lib.calls[0].request.url)

    @responses_lib.activate
    def test_context_manager_calls_unregister_on_exception(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        with self.assertRaises(RuntimeError):
            with jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client"):
                raise RuntimeError("simulated failure")
        self.assertEqual(len(responses_lib.calls), 1)
        self.assertIn("unregister", responses_lib.calls[0].request.url)

    @responses_lib.activate
    def test_context_manager_returns_instance(self):
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        with jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client") as j:
            self.assertIsInstance(j, jordan.JordanInstance)


class TestMessageStateTransitions(unittest.TestCase):

    def _make_msg(self) -> jordan.JordanMessage:
        msg_dict = {
            "messageId": MSG_ID,
            "action": {"actionName": "shoot", "placeholders": {"player": "Jordan", "points": "3"}},
        }
        return jordan.JordanMessage(BASE_URL, TASK_ID, msg_dict, AUTH_TOKEN)

    @responses_lib.activate
    def test_acknowledge(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_ACKNOWLEDGED}"),
            status=202,
        )
        self.assertTrue(self._make_msg().acknowledge())

    @responses_lib.activate
    def test_processed(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_PROCESSED}"),
            status=202,
        )
        self.assertTrue(self._make_msg().processed())

    @responses_lib.activate
    def test_cannot_process(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.CANNOT_PROCESS_MESSAGE}"),
            status=202,
        )
        self.assertTrue(self._make_msg().cannot_process())

    @responses_lib.activate
    def test_overridden(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_OVERRIDDEN}"),
            status=202,
        )
        self.assertTrue(self._make_msg().overridden())

    @responses_lib.activate
    def test_acknowledge_and_processed_full_flow(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_ACKNOWLEDGED}"),
            status=202,
        )
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_PROCESSED}"),
            status=202,
        )
        self.assertTrue(self._make_msg().acknowledge_and_processed())
        self.assertEqual(len(responses_lib.calls), 2)

    @responses_lib.activate
    def test_acknowledge_and_processed_stops_if_ack_fails(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_ACKNOWLEDGED}"),
            status=500,
        )
        self.assertFalse(self._make_msg().acknowledge_and_processed())
        self.assertEqual(len(responses_lib.calls), 1)

    def test_placeholders_accessible_as_attributes(self):
        msg = self._make_msg()
        self.assertEqual(msg.placeholders.player, "Jordan")
        self.assertEqual(msg.placeholders.points, "3")

    def test_placeholders_get_and_has_key(self):
        msg = self._make_msg()
        self.assertTrue(msg.placeholders.has_key("player"))
        self.assertFalse(msg.placeholders.has_key("missing"))
        self.assertEqual(msg.placeholders.get("player"), "Jordan")

    @responses_lib.activate
    def test_update_message_failure_returns_false(self):
        responses_lib.add(
            responses_lib.PUT,
            _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_ACKNOWLEDGED}"),
            status=404,
        )
        self.assertFalse(self._make_msg().acknowledge())


class TestRequestArguments(unittest.TestCase):
    """A timeout given to a call reaches every request the call makes. requests has no default
    one: a call that drops it waits forever on a server that accepts the connection and never
    answers — which freezes the loop reading messages (JRD-15)."""

    TIMEOUT = 3.5

    def _make_instance(self, cls=jordan.JordanInstance) -> jordan.JordanInstance:
        return cls(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client")

    def _make_msg(self) -> jordan.JordanMessage:
        msg_dict = {"messageId": MSG_ID, "action": {"actionName": "stop", "placeholders": {}}}
        return jordan.JordanMessage(BASE_URL, TASK_ID, msg_dict, AUTH_TOKEN)

    def _accept_message_state(self, state: str) -> None:
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{MSG_ID}/{state}"), status=202)

    def _serve_a_message(self) -> None:
        msg_payload = {"messageId": MSG_ID, "action": {"actionName": "stop", "placeholders": {}}}
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=msg_payload, status=200)
        self._accept_message_state(jordan.MESSAGE_CLIENT_RECEIVED)

    def _assert_every_request_bounded(self, expected_calls: int) -> None:
        self.assertEqual(len(responses_lib.calls), expected_calls)
        for call in responses_lib.calls:
            with self.subTest(url=call.request.url):
                self.assertEqual(call.request.req_kwargs["timeout"], self.TIMEOUT)

    @responses_lib.activate
    def test_read_message_bounds_the_read_and_the_receipt(self):
        self._serve_a_message()
        self.assertIsNotNone(self._make_instance().read_message(timeout=self.TIMEOUT))
        self._assert_every_request_bounded(2)

    @responses_lib.activate
    def test_read_message_bounds_the_read_when_there_is_no_message(self):
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), status=204)
        self.assertIsNone(self._make_instance().read_message(timeout=self.TIMEOUT))
        self._assert_every_request_bounded(1)

    @responses_lib.activate
    def test_read_message_async_bounds_its_requests_too(self):
        self._serve_a_message()
        delivered = threading.Event()
        self._make_instance().read_message(async_callback=lambda _msg: delivered.set(), timeout=self.TIMEOUT)
        self.assertTrue(delivered.wait(5))
        self._assert_every_request_bounded(2)

    @responses_lib.activate
    def test_message_state_changes_bound_their_request(self):
        for state, change in [
            (jordan.MESSAGE_STATE_ACKNOWLEDGED, jordan.JordanMessage.acknowledge),
            (jordan.MESSAGE_STATE_PROCESSED, jordan.JordanMessage.processed),
            (jordan.MESSAGE_CLIENT_RECEIVED, jordan.JordanMessage.received),
            (jordan.CANNOT_PROCESS_MESSAGE, jordan.JordanMessage.cannot_process),
            (jordan.MESSAGE_OVERRIDDEN, jordan.JordanMessage.overridden),
        ]:
            with self.subTest(state=state):
                responses_lib.reset()
                self._accept_message_state(state)
                self.assertTrue(change(self._make_msg(), timeout=self.TIMEOUT))
                self._assert_every_request_bounded(1)

    @responses_lib.activate
    def test_acknowledge_and_processed_bounds_both_requests(self):
        self._accept_message_state(jordan.MESSAGE_STATE_ACKNOWLEDGED)
        self._accept_message_state(jordan.MESSAGE_STATE_PROCESSED)
        self.assertTrue(self._make_msg().acknowledge_and_processed(timeout=self.TIMEOUT))
        self._assert_every_request_bounded(2)

    @responses_lib.activate
    def test_complete_bounds_its_request(self):
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{jordan.TASK_STATE_COMPLETE}"), status=202)
        self.assertTrue(self._make_instance().complete(timeout=self.TIMEOUT))
        self._assert_every_request_bounded(1)

    def _accept_failure_report(self) -> None:
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), json={"statusId": "s"}, status=200)
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{jordan.TASK_STATE_ERROR}"), status=202)

    @responses_lib.activate
    def test_fatal_bounds_its_three_requests(self):
        self._accept_failure_report()
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)
        self._make_instance().fatal(RuntimeError("diverged"), timeout=self.TIMEOUT)
        self._assert_every_request_bounded(3)

    @responses_lib.activate
    def test_fatal_of_a_sub_task_bounds_its_two_requests(self):
        """A sub-task is only marked ERROR: it has nothing to unregister."""
        self._accept_failure_report()
        self._make_instance(jordan.JordanTaskInstance).fatal(RuntimeError("diverged"), timeout=self.TIMEOUT)
        self._assert_every_request_bounded(2)

    @responses_lib.activate
    def test_async_status_sends_bound_their_request(self):
        """The asynchronous path runs the request on a thread of its own: it has to carry the
        arguments there too (JRD-17)."""
        for name, send in [
            ("send_status", lambda instance, **kw: instance.send_status("epoch 3", **kw)),
            ("send_progress", lambda instance, **kw: instance.send_progress(42, **kw)),
            ("send_success_status", lambda instance, **kw: instance.send_success_status("done", **kw)),
            ("send_failure_status", lambda instance, **kw: instance.send_failure_status("diverged", **kw)),
            ("send_typed_status", lambda instance, **kw: instance.send_typed_status(jordan.GENERAL_STATUS_TYPE, "x", **kw)),
            ("send_metric", lambda instance, **kw: instance.send_metric("loss", 0.5, step=3, **kw)),
        ]:
            with self.subTest(call=name):
                responses_lib.reset()
                responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), json={"statusId": "s"}, status=200)
                sent = threading.Event()
                send(self._make_instance(), async_callback=lambda _status_id: sent.set(), timeout=self.TIMEOUT)
                self.assertTrue(sent.wait(5))
                self._assert_every_request_bounded(1)


class TestDefaultRequestTimeout(unittest.TestCase):
    """A call that passes no timeout still gets one: 30 s, or JORDAN_REQUEST_TIMEOUT. Without
    it, a server that accepts the connection and never answers holds the call forever (JRD-18)."""

    def setUp(self):
        self._saved = os.environ.pop(jordan.REQUEST_TIMEOUT_ENV_VAR, None)

    def tearDown(self):
        os.environ.pop(jordan.REQUEST_TIMEOUT_ENV_VAR, None)
        if self._saved is not None:
            os.environ[jordan.REQUEST_TIMEOUT_ENV_VAR] = self._saved

    def _make_instance(self) -> jordan.JordanInstance:
        return jordan.JordanInstance(BASE_URL, TASK_ID, AUTH_TOKEN, "test-client")

    def _serve_a_message(self) -> None:
        msg_payload = {"messageId": MSG_ID, "action": {"actionName": "stop", "placeholders": {}}}
        responses_lib.add(responses_lib.GET, _url(f"client/{TASK_ID}/message"), json=msg_payload, status=200)
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_CLIENT_RECEIVED}"), status=202)

    def _timeouts(self):
        return [call.request.req_kwargs["timeout"] for call in responses_lib.calls]

    @responses_lib.activate
    def test_every_call_without_a_timeout_gets_the_default(self):
        responses_lib.add(responses_lib.POST, _url("client/register"), json={"taskId": TASK_ID, "authToken": AUTH_TOKEN}, status=200)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/task"), json={"taskId": "sub-1"}, status=201)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/status"), json={"statusId": "s"}, status=200)
        self._serve_a_message()
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{MSG_ID}/{jordan.MESSAGE_STATE_ACKNOWLEDGED}"), status=202)
        responses_lib.add(responses_lib.PUT, _url(f"client/{TASK_ID}/{jordan.TASK_STATE_COMPLETE}"), status=202)
        responses_lib.add(responses_lib.POST, _url(f"client/{TASK_ID}/unregister"), status=200)

        instance = jordan.register(BASE_URL)
        instance.create_task("sub")
        instance.send_status("epoch 3")
        instance.read_message().acknowledge()
        instance.complete()
        instance.unregister()

        self.assertEqual(len(responses_lib.calls), 8)
        self.assertEqual(self._timeouts(), [jordan.DEFAULT_REQUEST_TIMEOUT] * 8)
        self.assertEqual(jordan.DEFAULT_REQUEST_TIMEOUT, 30.0)

    @responses_lib.activate
    def test_the_asynchronous_path_gets_the_default_too(self):
        self._serve_a_message()
        delivered = threading.Event()
        self._make_instance().read_message(async_callback=lambda _msg: delivered.set())
        self.assertTrue(delivered.wait(5))
        self.assertEqual(self._timeouts(), [jordan.DEFAULT_REQUEST_TIMEOUT] * 2)

    @responses_lib.activate
    def test_the_environment_variable_replaces_the_default(self):
        os.environ[jordan.REQUEST_TIMEOUT_ENV_VAR] = " 4.5 "
        self._serve_a_message()
        self._make_instance().read_message()
        self.assertEqual(self._timeouts(), [4.5, 4.5])

    @responses_lib.activate
    def test_an_explicit_timeout_wins_over_the_environment(self):
        os.environ[jordan.REQUEST_TIMEOUT_ENV_VAR] = "4.5"
        self._serve_a_message()
        self._make_instance().read_message(timeout=(2, 9))
        self.assertEqual(self._timeouts(), [(2, 9), (2, 9)])

    @responses_lib.activate
    def test_timeout_none_still_waits_forever(self):
        self._serve_a_message()
        self._make_instance().read_message(timeout=None)
        self.assertEqual(self._timeouts(), [None, None])

    @responses_lib.activate
    def test_an_empty_variable_means_the_default(self):
        os.environ[jordan.REQUEST_TIMEOUT_ENV_VAR] = ""
        self._serve_a_message()
        self._make_instance().read_message()
        self.assertEqual(self._timeouts(), [jordan.DEFAULT_REQUEST_TIMEOUT] * 2)

    @responses_lib.activate
    def test_an_unusable_variable_raises_before_any_request(self):
        for value in ["abc", "0", "-1", "nan", "inf"]:
            with self.subTest(value=value):
                responses_lib.reset()
                os.environ[jordan.REQUEST_TIMEOUT_ENV_VAR] = value
                with self.assertRaisesRegex(ValueError, jordan.REQUEST_TIMEOUT_ENV_VAR):
                    self._make_instance().send_status("epoch 3")
                self.assertEqual(len(responses_lib.calls), 0)

    @responses_lib.activate
    def test_the_caller_arguments_are_left_untouched(self):
        self._serve_a_message()
        arguments = {"verify": False}
        self._make_instance().read_message(**arguments)
        self.assertEqual(arguments, {"verify": False})
        self.assertEqual(self._timeouts(), [jordan.DEFAULT_REQUEST_TIMEOUT] * 2)


if __name__ == '__main__':
    unittest.main()
