from time import time
from collections.abc import Callable
from typing import Any
import json
import logging
import math
import os
import requests
import threading
import types


DEFAULT_CLIENT_NAME = "default-client"
DEFAULT_NO_ACTION: list[Any] = []
DEFAULT_NO_PASSWORD: str | None = None

# Servers that closed registration expect this key; the variable lets a program
# register without passing it explicitly through the code.
REGISTRATION_KEY_ENV_VAR = 'JORDAN_REGISTRATION_KEY'

# requests has no default timeout, so a server that accepts the connection and stops
# answering would hold a call forever (JRD-18). A call's own ``timeout=`` wins, ``None``
# included; otherwise the variable, then this default — the same name and value as jordan_cli.
REQUEST_TIMEOUT_ENV_VAR = 'JORDAN_REQUEST_TIMEOUT'
DEFAULT_REQUEST_TIMEOUT = 30.0

PARAMETER_TYPE_STRING = 'string'
PARAMETER_TYPE_INT = 'int'
PARAMETER_TYPE_FLOAT = 'float'

FAILURE_STATUS_TYPE = 'failure'
SUCCESS_STATUS_TYPE = 'success'
GENERAL_STATUS_TYPE = 'general'
PROGRESS_STATUS_TYPE = 'progress'
METRIC_STATUS_TYPE = 'metric'
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

logger = logging.getLogger(__name__)


class UndecodableMessageError(ValueError):
    """The server answered a read with a message the library could not decode — a body cut
    short, not JSON, or missing a field. The server took that message off the queue when it
    answered, so the raw ``body`` is the only copy left: it is kept here, and logged before this
    is raised (JRD-29). A ``ValueError``, as the ``JSONDecodeError`` it replaces."""

    def __init__(self, task_id: str, status_code: int, body: str, cause: Exception) -> None:
        super().__init__(f"Could not decode the message read for task {task_id} "
                         f"(HTTP {status_code}, {type(cause).__name__}: {cause}); raw body: {body!r}")
        self.task_id = task_id
        self.status_code = status_code
        self.body = body


def with_action(action_name: str) -> 'ActionBuilder':
    return ActionBuilder().with_action(action_name)


class ActionBuilder:

    def __init__(self) -> None:
        self.actions: dict[str, dict[str, Any]] = {}
        self.current_action_name: str | None = None

    def with_action(self, action_name: str) -> 'ActionBuilder':
        self.actions[action_name] = {}
        self.current_action_name = action_name
        return self

    def with_parameter(self, parameter_name: str, parameter_type: str = PARAMETER_TYPE_STRING, default_value: str | None = None) -> 'ActionBuilder':
        valid_parameter_types = [PARAMETER_TYPE_STRING, PARAMETER_TYPE_INT, PARAMETER_TYPE_FLOAT]
        if parameter_type not in valid_parameter_types:
            raise ValueError(f"Parameter {parameter_name} of type {parameter_type} must be one of {valid_parameter_types}")
        self.actions[self.current_action_name][parameter_name] = parameter_type, default_value
        return self

    def build(self) -> list[dict[str, Any]]:
        self.current_action_name = None
        actions = []
        for action_name, parameters in self.actions.items():
            action_definition: dict[str, Any] = {'actionName': action_name}
            if len(parameters) > 0:
                action_definition['parameters'] = []
                for param_name, (param_type, param_default_value) in parameters.items():
                    action_parameter_definition: dict[str, Any] = {'name': param_name, 'type': param_type}
                    if param_default_value:
                        action_parameter_definition['defaultValue'] = param_default_value
                    action_definition['parameters'].append(action_parameter_definition)
            actions.append(action_definition)
        return actions


class JordanMessagePlaceholders:
    def __init__(self, placeholders_dict: dict[str, Any]) -> None:
        for k, v in placeholders_dict.items():
            setattr(self, k, v)
        self.placehoders = placeholders_dict

    def get(self, key: str) -> Any:
        return self.placehoders[key]

    def has_key(self, key: str) -> bool:
        return key in self.placehoders


class JordanMessage:
    def __init__(self, base_url: str, task_id: str, msg: dict[str, Any], auth_token: str | None = None) -> None:
        self.base_url = base_url
        if not self.base_url.endswith('/'):
            self.base_url += '/'
        self.task_id = task_id
        self.auth_token = auth_token
        self.message_id = msg['messageId']
        self.action_name = msg['action']['actionName']
        # optional in the contract: an action without them is an action without parameters
        self.placeholders = JordanMessagePlaceholders(msg['action'].get('placeholders') or {})
        # whether the server confirmed CLIENT_RECEIVED, and the request error of the last attempt
        # if it raised one — both written by received() alone
        self.receipt_confirmed = False
        self.receipt_error: requests.exceptions.RequestException | None = None

    def _auth_headers(self) -> dict[str, str]:
        if self.auth_token:
            return {'Authorization': f'Bearer {self.auth_token}'}
        return {}

    def acknowledge_and_processed(self, **kwargs: Any) -> bool:
        ack = self.acknowledge(**kwargs)
        return self.processed(**kwargs) if ack else ack

    def acknowledge(self, **kwargs: Any) -> bool:
        return self.update_message(MESSAGE_STATE_ACKNOWLEDGED, **kwargs)

    def processed(self, **kwargs: Any) -> bool:
        return self.update_message(MESSAGE_STATE_PROCESSED, **kwargs)

    def received(self, **kwargs: Any) -> bool:
        """Tell the server the message reached the program, at best effort: a request error is
        kept in ``receipt_error`` rather than raised, since the message is already in the
        program's hands (JRD-19). ``read_message`` sends it; calling it again retries one that
        was not confirmed. Returns ``receipt_confirmed``.

        After a ``requests.exceptions.Timeout`` the outcome is unknown, not negative: the server
        may have recorded the receipt and only its answer was lost — a retry then records it
        twice in the message's history."""
        self.receipt_error = None
        try:
            self.receipt_confirmed = self.update_message(MESSAGE_CLIENT_RECEIVED, **kwargs)
        except requests.exceptions.RequestException as error:
            self.receipt_confirmed = False
            self.receipt_error = error
        return self.receipt_confirmed

    def cannot_process(self, **kwargs: Any) -> bool:
        return self.update_message(CANNOT_PROCESS_MESSAGE, **kwargs)

    def overridden(self, **kwargs: Any) -> bool:
        return self.update_message(MESSAGE_OVERRIDDEN, **kwargs)

    def update_message(self, message_state: str, **kwargs: Any) -> bool:
        """``kwargs`` go to ``requests`` as they are — ``timeout=5`` bounds the call, which
        otherwise gets ``default_request_timeout()``."""
        UPDATE_MESSAGE_STATE_ENDPOINT = self.base_url + UPDATE_MESSAGE_STATE_RESOURCE.format(self.task_id, self.message_id, message_state)
        r = requests.put(UPDATE_MESSAGE_STATE_ENDPOINT, headers=self._auth_headers(), **_with_default_timeout(kwargs))
        return r.status_code == 202


class JordanInstance:

    def __init__(self, base_url: str, task_id: str, auth_token: str, instance_name: str) -> None:
        self.base_url = base_url
        self.task_id = task_id
        self.auth_token = auth_token
        self.instance_name = instance_name

    def __enter__(self) -> 'JordanInstance':
        return self

    def __exit__(self, exc_type: type | None, exc_val: BaseException | None, exc_tb: types.TracebackType | None) -> None:
        self.unregister()

    def _auth_headers(self) -> dict[str, str]:
        return {'Authorization': f'Bearer {self.auth_token}'}

    def create_task(self, task_name: str, actions: list[Any] | None = None, password: str | None = DEFAULT_NO_PASSWORD, **kwargs: Any) -> 'JordanTaskInstance | None':
        if actions is None:
            actions = []
        NEW_TASK_ENDPOINT = self.base_url + NEW_TASK_RESOURCE.format(self.task_id)

        payload: dict[str, Any] = {'name': task_name}
        if password:
            payload['password'] = password
        if len(actions) > 0:
            payload['actions'] = actions

        r = requests.post(NEW_TASK_ENDPOINT, json=payload, headers=self._auth_headers(), **_with_default_timeout(kwargs))

        if r.status_code == 201:
            new_task_output = json.loads(r.text)
            return JordanTaskInstance(self.base_url, new_task_output['taskId'], self.auth_token, task_name)
        return None

    def send_status(self, status: str, status_type: str = DEFAULT_STATUS_TYPE, **kwargs: Any) -> str | None:
        """Equivalent to send_typed_status(status_type, status)"""
        return self.send_typed_status(status_type, status, **kwargs)

    def send_progress(self, percent: int | float | str, **kwargs: Any) -> str | None:
        """Send how far the task is, from 0 to 100: the task's progress bar in active clients.

        ``percent`` is a number, or a text holding one (``'42'``, ``'42%'``); it is sent as an
        integer, truncated so that a task reads 100 only once it is. Raises ``ValueError`` for
        anything else — a fraction such as ``0.65`` is read as 0.65 %, not 65 %."""
        return self.send_typed_status(PROGRESS_STATUS_TYPE, percent, **kwargs)

    def send_success_status(self, status: str, **kwargs: Any) -> str | None:
        return self.send_typed_status(SUCCESS_STATUS_TYPE, status, **kwargs)

    def send_failure_status(self, status: str, **kwargs: Any) -> str | None:
        return self.send_typed_status(FAILURE_STATUS_TYPE, status, **kwargs)

    def send_metric(self, name: str, value: float, step: float | None = None, async_call: bool = False, async_callback: Callable[[str], None] | None = None, **kwargs: Any) -> str | None:
        """Send a named value, which an active client draws as a curve: one curve per name.

        ``step`` is the progress point the value belongs to (the epoch, the iteration). It is
        optional: without it, the value is placed in time. The status also carries a readable
        text, so a client reading statuses as log lines shows the value as well.

        Returns the status id, or None when the value was not sent or was refused. A value or
        step that is not a finite number (NaN, infinity — what a diverging training reports) is
        not sent at all: no curve can hold it, and JSON cannot carry it, so the request would
        raise in the middle of the loop that sends it."""
        metric: dict[str, Any] = {'name': name, 'value': _json_number(value)}
        if step is not None:
            metric['step'] = _json_number(step)
        if not all(math.isfinite(number) for number in (metric['value'], metric.get('step', 0))):
            return None
        text = f"{name} = {metric['value']}" + (f" (step {metric['step']})" if step is not None else '')
        payload = {'type': METRIC_STATUS_TYPE, 'status': text, 'metric': metric}
        return self._send_status_payload(payload, async_call, async_callback, **kwargs)

    def send_typed_status(self, status_type: str, status: Any, async_call: bool = False, async_callback: Callable[[str], None] | None = None, **kwargs: Any) -> str | None:
        if status_type == PROGRESS_STATUS_TYPE:
            # the server moves the task's progress on an integer only, and logs anything else
            status = _progress_percent(status)
        return self._send_status_payload({'type': status_type, 'status': status}, async_call, async_callback, **kwargs)

    def _send_status_payload(self, payload: dict[str, Any], async_call: bool, async_callback: Callable[[str], None] | None, **kwargs: Any) -> str | None:
        if async_call or async_callback:
            threading.Thread(target=self._exec_send_status, args=[payload, async_callback], kwargs=kwargs).start()
            return None
        return self._exec_send_status(payload, **kwargs)

    def _exec_send_status(self, payload: dict[str, Any], async_callback: Callable[[str], None] | None = None, **kwargs: Any) -> str | None:
        STATUS_ENDPOINT = self.base_url + STATUS_RESOURCE.format(self.task_id)
        payload = dict(payload, timestamp=int(time()))
        r = requests.post(STATUS_ENDPOINT, json=payload, headers=self._auth_headers(), **_with_default_timeout(kwargs))

        if r.status_code == 200:
            status_output = json.loads(r.text)
            if async_callback:
                async_callback(status_output['statusId'])
            return status_output['statusId']

        return None

    def _exec_read_message(self, async_callback: Callable[['JordanMessage'], None] | None = None, send_receipt: bool = True, **kwargs: Any) -> 'JordanMessage | None':
        MESSAGE_ENDPOINT = self.base_url + MESSAGE_RESOURCE.format(self.task_id)
        r = requests.get(MESSAGE_ENDPOINT, headers=self._auth_headers(), **_with_default_timeout(kwargs))
        if r.status_code == 200:
            try:
                msg = JordanMessage(self.base_url, self.task_id, json.loads(r.text), self.auth_token)
            except (ValueError, KeyError, TypeError, AttributeError) as error:
                # the server already took the message off the queue: its body is the only copy
                # left. Logged as well as raised, since nobody catches it on the async path
                logger.error("Could not decode the message read for task %s (HTTP %s, %s: %s); raw body: %r",
                             self.task_id, r.status_code, type(error).__name__, error, r.text)
                raise UndecodableMessageError(self.task_id, r.status_code, r.text, error) from error
            if send_receipt:
                # a request too, bounded by the same arguments; it never raises, since the
                # server took the message off the queue when it answered the read (JRD-19)
                msg.received(**kwargs)
            if async_callback:
                async_callback(msg)
            return msg

        return None

    def read_message(self, async_call: bool = False, async_callback: Callable[['JordanMessage'], None] | None = None, send_receipt: bool = True, **kwargs: Any) -> 'JordanMessage | None':
        """Read the next message, if any. ``kwargs`` go to ``requests``, for the read and for the
        acknowledgement of receipt it sends: ``read_message(timeout=5)`` never waits more than
        five seconds per request on a server that stopped answering — and raises
        ``requests.exceptions.Timeout`` when the read does. Without it, each request gets
        ``default_request_timeout()``; ``timeout=None`` waits forever.

        The server hands a message out once: reading it takes it off the queue. So a message the
        server handed out and the library could decode is returned even when its acknowledgement
        of receipt then fails — ``msg.receipt_confirmed`` is False, ``msg.receipt_error`` holds
        the request error if there was one, and ``msg.received()`` sends it again.

        ``send_receipt=False`` leaves the acknowledgement to the caller, for one that bounds it
        differently from the read: ``msg.received(timeout=...)``.

        A message handed out that the library cannot decode raises ``UndecodableMessageError``,
        whose ``body`` holds the raw answer, and is logged at ERROR on the ``jordan_py.jordan``
        logger first — on the asynchronous path the log is the only trace left."""
        if async_call or async_callback:
            threading.Thread(target=self._exec_read_message, args=[async_callback, send_receipt], kwargs=kwargs).start()
            return None
        return self._exec_read_message(send_receipt=send_receipt, **kwargs)

    def unregister(self, **kwargs: Any) -> bool:
        UNREGISTER_ENDPOINT = self.base_url + UNREGISTER_RESOURCE.format(self.task_id)
        r = requests.post(UNREGISTER_ENDPOINT, headers=self._auth_headers(), **_with_default_timeout(kwargs))
        return r.status_code == 200

    def fatal(self, exception: Exception, **kwargs: Any) -> None:
        """Report the failure, mark the task ERROR and unregister: three requests, each given
        ``kwargs``."""
        self.send_failure_status(str(exception), **kwargs)
        self.update_task(TASK_STATE_ERROR, **kwargs)
        self.unregister(**kwargs)

    def update_task(self, task_state: str, **kwargs: Any) -> bool:
        UPDATE_TASK_STATE_ENDPOINT = self.base_url + UPDATE_TASK_STATE_RESOURCE.format(self.task_id, task_state)
        r = requests.put(UPDATE_TASK_STATE_ENDPOINT, headers=self._auth_headers(), **_with_default_timeout(kwargs))
        return r.status_code == 202

    def complete(self, **kwargs: Any) -> bool:
        return self.update_task(TASK_STATE_COMPLETE, **kwargs)


class JordanTaskInstance(JordanInstance):

    def fatal(self, exception: Exception, **kwargs: Any) -> None:
        self.send_failure_status(str(exception), **kwargs)
        self.update_task(TASK_STATE_ERROR, **kwargs)


def _json_number(value: Any) -> Any:
    """A number the JSON encoder can write. Numpy and torch scalars — what a training loop
    usually holds — are neither int nor float to it, and would fail the request."""
    if isinstance(value, (int, float)):
        return value
    return float(value)


def _progress_percent(value: Any) -> int:
    """The progress the server stores: an integer from 0 to 100. Numbers, numpy scalars and
    texts such as '42' or '42%' are read; the value is truncated, not rounded, so 99.6 stays 99."""
    number: float | None = None
    if isinstance(value, str):
        text = value.strip()
        try:
            number = float(text[:-1] if text.endswith('%') else text)
        except ValueError:
            pass
    elif not isinstance(value, bool):
        try:
            number = float(value)
        except (TypeError, ValueError):
            pass
    if number is None or not math.isfinite(number) or not 0 <= number <= 100:
        raise ValueError(f"progress must be a number from 0 to 100, got {value!r}")
    return int(number)


def default_request_timeout() -> float:
    """The timeout a call gets when it passes none: ``JORDAN_REQUEST_TIMEOUT`` in seconds if
    set, ``DEFAULT_REQUEST_TIMEOUT`` otherwise. Read on each call, like the registration key.
    A value that is not a finite number greater than 0 raises ``ValueError`` rather than being
    ignored: 0 would make requests fail at once, not wait forever."""
    raw = os.environ.get(REQUEST_TIMEOUT_ENV_VAR, '').strip()
    if not raw:
        return DEFAULT_REQUEST_TIMEOUT
    try:
        value = float(raw)
    except ValueError:
        value = math.nan
    if not math.isfinite(value) or value <= 0:
        raise ValueError(f'{REQUEST_TIMEOUT_ENV_VAR}={raw!r}: expected a number of seconds greater than 0')
    return value


def _with_default_timeout(kwargs: dict[str, Any]) -> dict[str, Any]:
    """The ``requests`` arguments of a call, with the default timeout when it set none."""
    if 'timeout' in kwargs:
        return kwargs
    return {**kwargs, 'timeout': default_request_timeout()}


def _registration_headers(registration_key: str | None) -> dict[str, str]:
    """Registration is open unless the server sets JORDAN_REGISTRATION_KEY, in
    which case it expects that key as a bearer token. Sending it when the server
    asks for nothing is harmless, so the environment variable is used as a
    fallback and no header is sent when neither is set."""
    key = registration_key if registration_key is not None else os.environ.get(REGISTRATION_KEY_ENV_VAR, '')
    return {'Authorization': f'Bearer {key}'} if key else {}


def register(server_base_url: str, client_name: str = DEFAULT_CLIENT_NAME, actions: list[Any] = DEFAULT_NO_ACTION, password: str | None = DEFAULT_NO_PASSWORD, registration_key: str | None = None, **kwargs: Any) -> JordanInstance | None:
    REGISTER_ENDPOINT = server_base_url + REGISTER_RESOURCE

    payload: dict[str, Any] = {'name': client_name}
    if password:
        payload['password'] = password
    if len(actions) > 0:
        payload['actions'] = actions

    r = requests.post(REGISTER_ENDPOINT, json=payload, headers=_registration_headers(registration_key), **_with_default_timeout(kwargs))

    if r.status_code == 200:
        register_output = json.loads(r.text)
        return JordanInstance(server_base_url, register_output['taskId'], register_output['authToken'], client_name)

    return None
