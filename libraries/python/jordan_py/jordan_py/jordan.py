from time import time
from typing import Any, Callable, Dict, List, Optional
import json
import requests
import threading
import types


DEFAULT_CLIENT_NAME = "default-client"
DEFAULT_NO_ACTION: List[Any] = []
DEFAULT_NO_PASSWORD: Optional[str] = None

PARAMETER_TYPE_STRING = 'string'
PARAMETER_TYPE_INT = 'int'
PARAMETER_TYPE_FLOAT = 'float'

FAILURE_STATUS_TYPE = 'failure'
SUCCESS_STATUS_TYPE = 'success'
GENERAL_STATUS_TYPE = 'general'
PROGRESS_STATUS_TYPE = 'progress'
DEFAULT_STATUS_TYPE = GENERAL_STATUS_TYPE

CLIENT_NAMESPACE = 'client/'
REGISTER_RESOURCE = CLIENT_NAMESPACE + "register"

TASK_ID = '{}/'
NEW_TASK_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'task'
TASK_STATE = '{}'
UPDATE_TASK_STATE_RESOURCE = CLIENT_NAMESPACE + TASK_ID + TASK_STATE
STATUS_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'status'
MESSAGE_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'message'
MESSAGE_ID = '{}/'
MESSAGE_STATE = '{}'
UPDATE_MESSAGE_STATE_RESOURCE = CLIENT_NAMESPACE + TASK_ID + MESSAGE_ID + MESSAGE_STATE
CLIENT_ID = '{}/'
UNREGISTER_RESOURCE = CLIENT_NAMESPACE + CLIENT_ID + 'unregister'

TASK_STATE_COMPLETE = "COMPLETE"
TASK_STATE_ERROR = "ERROR"
MESSAGE_STATE_ACKNOWLEDGED = 'MESSAGE_ACKNOWLEDGED'
MESSAGE_STATE_PROCESSED = 'MESSAGE_PROCESSED'
MESSAGE_CLIENT_RECEIVED = 'CLIENT_RECEIVED'
MESSAGE_OVERRIDDEN = 'MESSAGE_OVERRIDDEN'
CANNOT_PROCESS_MESSAGE = 'ERROR_CANNOT_PROCESS_MESSAGE'


def with_action(action_name: str) -> 'ActionBuilder':
    return ActionBuilder().with_action(action_name)


class ActionBuilder:

    def __init__(self) -> None:
        self.actions: Dict[str, Dict[str, Any]] = {}
        self.current_action_name: Optional[str] = None

    def with_action(self, action_name: str) -> 'ActionBuilder':
        self.actions[action_name] = {}
        self.current_action_name = action_name
        return self

    def with_parameter(self, parameter_name: str, parameter_type: str = PARAMETER_TYPE_STRING, default_value: Optional[str] = None) -> 'ActionBuilder':
        valid_parameter_types = [PARAMETER_TYPE_STRING, PARAMETER_TYPE_INT, PARAMETER_TYPE_FLOAT]
        if parameter_type not in valid_parameter_types:
            raise ValueError(f"Parameter {parameter_name} of type {parameter_type} must be one of {valid_parameter_types}")
        self.actions[self.current_action_name][parameter_name] = parameter_type, default_value
        return self

    def build(self) -> List[Dict[str, Any]]:
        self.current_action_name = None
        actions = []
        for action_name, parameters in self.actions.items():
            action_definition: Dict[str, Any] = {'actionName': action_name}
            if len(parameters) > 0:
                action_definition['parameters'] = []
                for param_name, (param_type, param_default_value) in parameters.items():
                    action_parameter_definition: Dict[str, Any] = {'name': param_name, 'type': param_type}
                    if param_default_value:
                        action_parameter_definition['defaultValue'] = param_default_value
                    action_definition['parameters'].append(action_parameter_definition)
            actions.append(action_definition)
        return actions


class JordanMessagePlaceholders:
    def __init__(self, placeholders_dict: Dict[str, Any]) -> None:
        for k, v in placeholders_dict.items():
            setattr(self, k, v)
        self.placehoders = placeholders_dict

    def get(self, key: str) -> Any:
        return self.placehoders[key]

    def has_key(self, key: str) -> bool:
        return key in self.placehoders


class JordanMessage:
    def __init__(self, base_url: str, task_id: str, msg: Dict[str, Any], auth_token: Optional[str] = None) -> None:
        self.base_url = base_url
        self.task_id = task_id
        self.auth_token = auth_token
        self.message_id = msg['messageId']
        self.action_name = msg['action']['actionName']
        self.placeholders = JordanMessagePlaceholders(msg['action']['placeholders'])

    def _auth_headers(self) -> Dict[str, str]:
        if self.auth_token:
            return {'Authorization': f'Bearer {self.auth_token}'}
        return {}

    def acknowledge_and_processed(self) -> bool:
        ack = self.acknowledge()
        return self.processed() if ack else ack

    def acknowledge(self) -> bool:
        return self.update_message(MESSAGE_STATE_ACKNOWLEDGED)

    def processed(self) -> bool:
        return self.update_message(MESSAGE_STATE_PROCESSED)

    def received(self) -> bool:
        return self.update_message(MESSAGE_CLIENT_RECEIVED)

    def cannot_process(self) -> bool:
        return self.update_message(CANNOT_PROCESS_MESSAGE)

    def overridden(self) -> bool:
        return self.update_message(MESSAGE_OVERRIDDEN)

    def update_message(self, message_state: str, **kwargs: Any) -> bool:
        UPDATE_MESSAGE_STATE_ENDPOINT = self.base_url + UPDATE_MESSAGE_STATE_RESOURCE.format(self.task_id, self.message_id, message_state)
        r = requests.put(UPDATE_MESSAGE_STATE_ENDPOINT, headers=self._auth_headers(), **kwargs)
        return r.status_code == 202


class JordanInstance:

    def __init__(self, base_url: str, task_id: str, auth_token: str, instance_name: str) -> None:
        self.base_url = base_url
        self.task_id = task_id
        self.auth_token = auth_token
        self.instance_name = instance_name

    def __enter__(self) -> 'JordanInstance':
        return self

    def __exit__(self, exc_type: Optional[type], exc_val: Optional[BaseException], exc_tb: Optional[types.TracebackType]) -> None:
        self.unregister()

    def _auth_headers(self) -> Dict[str, str]:
        return {'Authorization': f'Bearer {self.auth_token}'}

    def create_task(self, task_name: str, actions: Optional[List[Any]] = None, password: Optional[str] = DEFAULT_NO_PASSWORD, **kwargs: Any) -> Optional['JordanTaskInstance']:
        if actions is None:
            actions = []
        NEW_TASK_ENDPOINT = self.base_url + NEW_TASK_RESOURCE.format(self.task_id)

        payload: Dict[str, Any] = {'name': task_name}
        if password:
            payload['password'] = password
        if len(actions) > 0:
            payload['actions'] = actions

        r = requests.post(NEW_TASK_ENDPOINT, json=payload, headers=self._auth_headers(), **kwargs)

        if r.status_code == 201:
            new_task_output = json.loads(r.text)
            return JordanTaskInstance(self.base_url, new_task_output['taskId'], self.auth_token, task_name)
        return None

    def send_status(self, status: str, status_type: str = DEFAULT_STATUS_TYPE, **kwargs: Any) -> Optional[str]:
        """Equivalent to send_typed_status(status_type, status)"""
        return self.send_typed_status(status_type, status, **kwargs)

    def send_progress(self, status: str, **kwargs: Any) -> Optional[str]:
        return self.send_typed_status(PROGRESS_STATUS_TYPE, status, **kwargs)

    def send_success_status(self, status: str, **kwargs: Any) -> Optional[str]:
        return self.send_typed_status(SUCCESS_STATUS_TYPE, status, **kwargs)

    def send_failure_status(self, status: str, **kwargs: Any) -> Optional[str]:
        return self.send_typed_status(FAILURE_STATUS_TYPE, status, **kwargs)

    def send_typed_status(self, status_type: str, status: str, async_call: bool = False, async_callback: Optional[Callable[[str], None]] = None, **kwargs: Any) -> Optional[str]:
        if async_call or async_callback:
            threading.Thread(target=self._exec_send_typed_status, args=[status_type, status, async_callback]).start()
            return None
        return self._exec_send_typed_status(status_type, status, **kwargs)

    def _exec_send_typed_status(self, status_type: str, status: str, async_callback: Optional[Callable[[str], None]] = None, **kwargs: Any) -> Optional[str]:
        STATUS_ENDPOINT = self.base_url + STATUS_RESOURCE.format(self.task_id)
        timestamp = int(time())
        payload = {'type': status_type, 'status': status, 'timestamp': timestamp}
        r = requests.post(STATUS_ENDPOINT, json=payload, headers=self._auth_headers(), **kwargs)

        if r.status_code == 200:
            status_output = json.loads(r.text)
            if async_callback:
                async_callback(status_output['statusId'])
            return status_output['statusId']

        return None

    def _exec_read_message(self, async_callback: Optional[Callable[['JordanMessage'], None]] = None, **kwargs: Any) -> Optional['JordanMessage']:
        MESSAGE_ENDPOINT = self.base_url + MESSAGE_RESOURCE.format(self.task_id)
        r = requests.get(MESSAGE_ENDPOINT, headers=self._auth_headers(), **kwargs)
        if r.status_code == 200:
            message_output = json.loads(r.text)
            msg = JordanMessage(self.base_url, self.task_id, message_output, self.auth_token)
            msg.received()
            if async_callback:
                async_callback(msg)
            return msg

        return None

    def read_message(self, async_call: bool = False, async_callback: Optional[Callable[['JordanMessage'], None]] = None, **kwargs: Any) -> Optional['JordanMessage']:
        if async_call or async_callback:
            threading.Thread(target=self._exec_read_message, args=[async_callback]).start()
            return None
        return self._exec_read_message()

    def unregister(self, **kwargs: Any) -> bool:
        UNREGISTER_ENDPOINT = self.base_url + UNREGISTER_RESOURCE.format(self.task_id)
        r = requests.post(UNREGISTER_ENDPOINT, headers=self._auth_headers(), **kwargs)
        return r.status_code == 200

    def fatal(self, exception: Exception, **kwargs: Any) -> None:
        self.send_failure_status(str(exception))
        self.update_task(TASK_STATE_ERROR)
        self.unregister()

    def update_task(self, task_state: str, **kwargs: Any) -> bool:
        UPDATE_TASK_STATE_ENDPOINT = self.base_url + UPDATE_TASK_STATE_RESOURCE.format(self.task_id, task_state)
        r = requests.put(UPDATE_TASK_STATE_ENDPOINT, headers=self._auth_headers(), **kwargs)
        return r.status_code == 202

    def complete(self, **kwargs: Any) -> bool:
        return self.update_task(TASK_STATE_COMPLETE)


class JordanTaskInstance(JordanInstance):

    def fatal(self, exception: Exception, **kwargs: Any) -> None:
        self.send_failure_status(str(exception))
        self.update_task(TASK_STATE_ERROR)


def register(server_base_url: str, client_name: str = DEFAULT_CLIENT_NAME, actions: List[Any] = DEFAULT_NO_ACTION, password: Optional[str] = DEFAULT_NO_PASSWORD, **kwargs: Any) -> Optional[JordanInstance]:
    REGISTER_ENDPOINT = server_base_url + REGISTER_RESOURCE

    payload: Dict[str, Any] = {'name': client_name}
    if password:
        payload['password'] = password
    if len(actions) > 0:
        payload['actions'] = actions

    r = requests.post(REGISTER_ENDPOINT, json=payload, **kwargs)

    if r.status_code == 200:
        register_output = json.loads(r.text)
        return JordanInstance(server_base_url, register_output['taskId'], register_output['authToken'], client_name)

    return None
