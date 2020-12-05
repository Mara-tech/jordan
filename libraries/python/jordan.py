from time import time
import json
import requests
import threading


DEFAULT_CLIENT_NAME = "default-client"
DEFAULT_NO_ACTION = {}
DEFAULT_NO_PASSWORD = None

PARAMETER_TYPE_STRING = 'string'
PARAMETER_TYPE_INT = 'int'
PARAMETER_TYPE_FLOAT = 'float'
#more types : list, ... ?

FAILURE_STATUS_TYPE = 'failure'
SUCCESS_STATUS_TYPE = 'success'
GENERAL_STATUS_TYPE = 'general'
DEFAULT_STATUS_TYPE = GENERAL_STATUS_TYPE

CLIENT_NAMESPACE = 'client/'
REGISTER_RESOURCE = CLIENT_NAMESPACE + "register"

TASK_ID = '{}/'
NEW_TASK_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'task'
STATUS_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'status'
MESSAGE_RESOURCE = CLIENT_NAMESPACE + TASK_ID + 'message'
MESSAGE_ID = '{}/'
MESSAGE_STATE = '{}'
UPDATE_MESSAGE_STATE_RESOURCE = CLIENT_NAMESPACE + TASK_ID + MESSAGE_ID + MESSAGE_STATE
CLIENT_ID = '{}/'
UNREGISTER_RESOURCE = CLIENT_NAMESPACE + CLIENT_ID + 'unregister'


MESSAGE_STATE_ACKNOWLEDGED = 'MESSAGE_ACKNOWLEDGED'
MESSAGE_STATE_PROCESSED = 'MESSAGE_PROCESSED'

def with_action(action_name):
    return ActionBuilder().with_action(action_name)



class ActionBuilder():

    actions = {}

    def with_action(self, action_name):
        self.actions[action_name] = {}
        self.current_action_name = action_name
        return self


    def with_parameter(self, parameter_name, parameter_type=PARAMETER_TYPE_STRING):
        valid_parameter_types = [PARAMETER_TYPE_STRING, PARAMETER_TYPE_INT, PARAMETER_TYPE_FLOAT]
        if parameter_type not in valid_parameter_types:
            raise ValueError(f"Parameter {parameter_name} of type {parameter_type} must be one of {valid_parameter_types}")
        self.actions[self.current_action_name][parameter_name] = parameter_type
        return self

    def build(self):
        self.current_action_name = None
        actions = []
        for action_name, parameters in self.actions.items():
            action_definition = {'action_name':action_name}
            if len(parameters) > 0:
                action_definition['parameters'] = []
                for param_name, param_type in parameters.items():
                    action_parameter_definition = {'name' : param_name, 'type': param_type}
                    action_definition['parameters'].append(action_parameter_definition)
            actions.append(action_definition)
        return actions


class JordanMessagePlaceholders():
    def __init__(self, placeholders_dict):
        for k,v in placeholders_dict.items():
            setattr(self, k, v)
        self.placehoders = placeholders_dict

    def get(self, key):
        return self.placehoders[key]

    def has_key(self, key):
        return key in self.placehoders

class JordanMessage():
    def __init__(self, base_url, task_id, msg):
        self.base_url = base_url
        self.task_id = task_id
        self.message_id = msg['message_id']
        self.action_name = msg['action']['action_name']
        self.placeholders = JordanMessagePlaceholders(msg['action']['placeholders'])


    def acknowledge(self):
        return self.update_message(MESSAGE_STATE_ACKNOWLEDGED)

    def processed(self):
        return self.update_message(MESSAGE_STATE_PROCESSED)

    def update_message(self, message_state, **kwargs):
        UPDATE_MESSAGE_STATE_ENDPOINT = self.base_url + UPDATE_MESSAGE_STATE_RESOURCE.format(self.task_id, self.message_id, message_state)

        r = requests.put(UPDATE_MESSAGE_STATE_ENDPOINT, **kwargs)

        return r.status_code == 202



class JordanInstance():

    def __init__(self, base_url, task_id, auth_token):
        self.base_url = base_url
        self.task_id = task_id
        self.auth_token = auth_token


    def create_task(self, task_name, actions=DEFAULT_NO_ACTION, password=DEFAULT_NO_PASSWORD, **kwargs):
        NEW_TASK_ENDPOINT = self.base_url + NEW_TASK_RESOURCE.format(self.task_id)

        payload = {'name': task_name}
        if password:
            payload['password'] = password
        if len(actions) > 0:
            payload['actions'] = actions

        r = requests.post(NEW_TASK_ENDPOINT, json=payload, **kwargs)

        if r.status_code == 201:
            new_task_output = json.loads(r.text)
            return JordanInstance(self.base_url, new_task_output['task_id'], self.auth_token)
        return None

    def send_status(self, status, **kwargs):
        return self.send_typed_status(DEFAULT_STATUS_TYPE, status, **kwargs)

    def send_success_status(self, status, **kwargs):
        return self.send_typed_status(SUCCESS_STATUS_TYPE, status, **kwargs)

    def send_failure_status(self, status, **kwargs):
        return self.send_typed_status(FAILURE_STATUS_TYPE, status, **kwargs)

    def _exec_send_typed_status(self, status_type, status, async_callback=None, **kwargs):
        STATUS_ENDPOINT = self.base_url + STATUS_RESOURCE.format(self.task_id)
        timestamp = int(time())
        payload = {'type':status_type,
                   'status':status,
                   'timestamp':timestamp}
        r = requests.post(STATUS_ENDPOINT, json=payload, **kwargs)

        if r.status_code == 200:
            status_output = json.loads(r.text)
            if async_callback:
                async_callback(status_output['status_id'])
            return status_output['status_id']

        return None

    def send_typed_status(self, status_type, status, async_call=False, async_callback=None, **kwargs):
        if async_call or async_callback:
            threading.Thread(target=self._exec_send_typed_status, args=[status_type, status, async_callback], kwargs=kwargs).start()
        else:
            return self._exec_send_typed_status(status_type, status)

    def _exec_read_message(self, async_callback=None, **kwargs):
        MESSAGE_ENDPOINT = self.base_url + MESSAGE_RESOURCE.format(self.task_id)
        r = requests.get(MESSAGE_ENDPOINT, **kwargs)
        if r.status_code == 200:
            message_output = json.loads(r.text)
            msg = JordanMessage(self.base_url, self.task_id, message_output)
            if async_callback:
                async_callback(msg)
            return msg

        return None

    def read_message(self, async_call=False, async_callback=None, **kwargs):
        if async_call or async_callback:
            threading.Thread(target=self._exec_read_message, args=[async_callback], kwargs=kwargs).start()
        else:
            return self._exec_read_message()

    def unregister(self, **kwargs):
        UNREGISTER_ENDPOINT = self.base_url + UNREGISTER_RESOURCE.format(self.task_id)
        r = requests.post(UNREGISTER_ENDPOINT, **kwargs)
        return r.status_code == 200


def register(server_base_url, client_name=DEFAULT_CLIENT_NAME, actions=DEFAULT_NO_ACTION, password=DEFAULT_NO_PASSWORD, **kwargs):
    REGISTER_ENDPOINT = server_base_url + REGISTER_RESOURCE

    payload = {'name': client_name}
    if password:
        payload['password'] = password
    if len(actions) > 0:
        payload['actions'] = actions

    r = requests.post(REGISTER_ENDPOINT, json=payload, **kwargs)

    if r.status_code == 200:
        register_output = json.loads(r.text)
        return JordanInstance(server_base_url, register_output['task_id'], register_output['auth_token'])

    return None