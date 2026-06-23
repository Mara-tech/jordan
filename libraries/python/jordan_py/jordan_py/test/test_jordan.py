import json
import unittest

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
        self._make_instance().send_progress("50% done")
        payload = json.loads(responses_lib.calls[0].request.body)
        self.assertEqual(payload["type"], jordan.PROGRESS_STATUS_TYPE)

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


if __name__ == '__main__':
    unittest.main()
