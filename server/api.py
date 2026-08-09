from jordan_constants import *
import jordan_log as log

from rejson_interface import *

import admin_identity as identity

from flask import Flask, request
from flask_restx import Api, Resource, fields

import json
import os
from hashlib import sha256
from secrets import compare_digest, token_hex
from time import time

# Full example : https://flask-restplus.readthedocs.io/en/stable/example.html
# --> namespace, marshall (serialize), docs, params, etc.

#--------------------
#---API DEFINITION---
#--------------------
app = Flask(__name__)

BEARER_AUTHORIZATION = 'Bearer'
AUTHORIZATIONS = {
    BEARER_AUTHORIZATION: {
        'type': 'apiKey',
        'in': 'header',
        'name': 'Authorization',
        'description': "Value: 'Bearer <token>'. Under /client/*, the token issued at "
                       "registration. Under /admin/*, the session token returned by "
                       "/admin/login, or the shared bootstrap admin token.",
    }
}

api = Api(app,
          version='1',
          title='Jordan Server API',
          description='Interactions with Jordan server',
          # license='MIT',
          # contact='Pupu',
          # contact_url='https://github.com/Mara-tech/jordan',
          doc=JORDAN_OPEN_API_DOC_SUFFIX,
          prefix=JORDAN_API_PATH_PREFIX,
          authorizations=AUTHORIZATIONS
          )

client_ns = api.namespace('client', description='Client-side operations')
admin_ns = api.namespace('admin', description='Admin-side operations')


def _bearer_token(namespace):
    auth = request.headers.get('Authorization', '')
    if not auth.startswith('Bearer '):
        namespace.abort(401, 'Missing Authorization: Bearer <token> header')
    return auth[len('Bearer '):]


def _require_client_auth(task_id):
    token = _bearer_token(client_ns)
    if not validate_auth_token(task_id, token):
        client_ns.abort(401, 'Invalid authentication token')


def admin_token():
    """Shared admin token, read at request time: server/.env is only loaded
    when rejson_interface is imported, after jordan_constants."""
    return os.environ.get(JORDAN_ADMIN_TOKEN_ENV_VAR, '').strip()


# enough for an IPv6 address with an IPv4 suffix, the longest legitimate value
MAX_CALLER_ADDRESS_LENGTH = 45


def _int_env(var_name, default):
    """Integer setting read at request time, falling back to its default when
    the variable is unset or does not hold a number."""
    raw = os.environ.get(var_name, '').strip()
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError:
        log.error(f"{var_name}='{raw}' is not a number, falling back to {default}")
        return default


def _caller_address():
    """Address a request comes from, used to bucket registration attempts.

    Behind a reverse proxy (Railway, nginx) the peer is the proxy itself, so the
    real caller has to be read from X-Forwarded-For. The proxy *appends* what it
    saw, hence the last entry: anything a caller forges sits to its left.

    The result ends up in a Redis key, so it is capped: without a proxy in front,
    the header is whatever the caller decided to send."""
    forwarded = request.headers.get('X-Forwarded-For', '')
    if forwarded:
        return forwarded.split(',')[-1].strip()[:MAX_CALLER_ADDRESS_LENGTH]
    return request.remote_addr or 'unknown'


def _key_name(key):
    """Label for a key declared without one. A fingerprint rather than the key
    itself, so logs can tell two keys apart without publishing either."""
    return 'key#' + sha256(key.encode('utf-8')).hexdigest()[:8]


def _parse_registration_keys(raw):
    """(name, key) pairs out of the JORDAN_REGISTRATION_KEY value: one bare key,
    or a JSON object naming each of several.

    Names are not decoration — they are what the logs report, so a rotation can
    be finished on evidence rather than on hope. The object form is therefore the
    only multi-key form: it makes naming automatic and unique.

    All or nothing: a skipped entry would be a key its operator believes valid,
    silently refusing the clients that hold it. Anything unusable raises instead,
    which check_configuration() turns into a refusal to boot."""
    if raw.startswith('['):
        raise ConfigurationError(
            f"{JORDAN_REGISTRATION_KEY_ENV_VAR} holds a JSON array: several keys are named, "
            f'as {{"retiring": "<old>", "current": "<new>"}}')
    if not raw.startswith('{'):
        return [(_key_name(raw), raw)]  # a single key, the common case
    try:
        declared = json.loads(raw)
    except ValueError as invalid_json:
        raise ConfigurationError(
            f"{JORDAN_REGISTRATION_KEY_ENV_VAR} opens with '{{' but is not valid JSON"
        ) from invalid_json
    if not isinstance(declared, dict):
        raise ConfigurationError(
            f"{JORDAN_REGISTRATION_KEY_ENV_VAR} must hold a single key, or a JSON object "
            f"mapping a name to each key")
    if not declared:
        raise ConfigurationError(
            f"{JORDAN_REGISTRATION_KEY_ENV_VAR} declares no key at all: unset it to leave "
            f"registration open, rather than closing it to everybody")

    keys = []
    for name, key in declared.items():
        if not isinstance(key, str) or not key.strip():
            raise ConfigurationError(
                f"{JORDAN_REGISTRATION_KEY_ENV_VAR}: '{name}' carries no key")
        if not name.strip():
            raise ConfigurationError(
                f"{JORDAN_REGISTRATION_KEY_ENV_VAR}: a key was declared without a name")
        keys.append((name.strip(), key.strip()))
    return keys


def registration_keys():
    """Keys a passive client may present to register, as (name, key) pairs.

    JORDAN_REGISTRATION_KEY holds either one key, or a JSON object naming each of
    several ({"retiring": "<old>", "current": "<new>"}). Several is what makes a
    key replaceable without a flag day: publish the new one beside the old, move
    the clients over, then drop the old one. The names are what makes that
    migration observable — the logs say which key each client used, so the
    retired one is dropped once it stops being seen.

    Returns None when the variable is unset: registration is open, as the
    contract has it by default. Raises ConfigurationError when it is set to
    something unusable, which must never read as 'open'."""
    raw = os.environ.get(JORDAN_REGISTRATION_KEY_ENV_VAR, '').strip()
    if not raw:
        return None
    return _parse_registration_keys(raw)


def check_configuration():
    """Refuse to start on a setting that is present but unusable, rather than to
    discover it on the request that needs it — a registration that gets refused,
    or an operator account that turns out never to have existed.

    Called at import, not from start_api(): under gunicorn nothing calls
    start_api(), and production is exactly where the check matters. A container
    that dies here never passes its health check, so the bad deployment does not
    take traffic — the previous one keeps serving."""
    registration_keys()
    identity.load_operators()


def _require_registration_key():
    """Guard for POST /client/register.

    The key travels in the Authorization header rather than the payload: the
    registration payload is logged and stored in Redis as the client record."""
    try:
        keys = registration_keys()
    except ConfigurationError as unusable:
        # unreachable through a server that booted, since check_configuration()
        # runs at import — but a broken declaration must close registration, not
        # re-open it, whatever put the process in that state
        log.error(f"Refusing every registration: {unusable}")
        client_ns.abort(401, 'Registration keys are misconfigured on this server')
    if keys is None:
        return  # registration is open on this server
    # compare bytes: compare_digest rejects str holding non-ASCII characters
    presented = _bearer_token(client_ns).encode('utf-8')
    for name, key in keys:
        if compare_digest(presented, key.encode('utf-8')):
            log.info(f"Registration accepted with '{name}'")
            return
    log.error(f"Rejected registration from {_caller_address()}: invalid registration key")
    client_ns.abort(401, 'Invalid registration key')


def _enforce_registration_rate_limit():
    """Cap how many registrations a single caller can attempt per window.

    Counts every attempt, valid or not, so it also slows down guessing of
    JORDAN_REGISTRATION_KEY."""
    limit = _int_env(JORDAN_REGISTRATION_RATE_LIMIT_ENV_VAR, JORDAN_DEFAULT_REGISTRATION_RATE_LIMIT)
    if limit <= 0:
        return  # explicitly disabled
    window = _int_env(JORDAN_REGISTRATION_RATE_WINDOW_ENV_VAR, JORDAN_DEFAULT_REGISTRATION_RATE_WINDOW)
    if window <= 0:
        window = JORDAN_DEFAULT_REGISTRATION_RATE_WINDOW
    caller = _caller_address()
    if count_registration_attempt(caller, window) > limit:
        log.error(f"Rate-limited registration from {caller}: over {limit} attempt(s) in {window}s")
        client_ns.abort(429, f"Too many registration attempts, retry in {window}s at most")


def _authenticated_operator():
    """Identity behind an /jordan/admin/* call: either an operator session opened
    through /admin/login, or the shared bootstrap token."""
    token = _bearer_token(admin_ns)
    shared = admin_token()
    # compare bytes: compare_digest rejects str holding non-ASCII characters
    if shared and compare_digest(token.encode('utf-8'), shared.encode('utf-8')):
        return identity.shared_token_identity()
    operator = read_admin_session(token)
    # an unknown, expired or corrupted session record grants nothing
    if isinstance(operator, dict) and operator.get('permissions'):
        return operator
    admin_ns.abort(401, 'Invalid or expired admin token')


def _declared_operators():
    """Operator accounts, or none when their declaration is unusable.

    Unreachable through a server that booted, since check_configuration() runs at
    import — but a broken declaration must leave no account to log into, never
    open the namespace."""
    try:
        return identity.load_operators()
    except ConfigurationError as unusable:
        log.error(f"No operator account is usable: {unusable}")
        return {}


def _require_admin_auth(permission):
    """Guard for every /jordan/admin/* resource: authenticate the caller, then
    check the permission the resource needs. Returns the caller's identity.

    Fails closed: with neither operator accounts nor a shared token configured,
    the whole namespace is refused rather than served openly."""
    if not admin_token() and not _declared_operators():
        log.error(f"Neither {JORDAN_ADMIN_TOKEN_ENV_VAR} nor {JORDAN_ADMIN_USERS_ENV_VAR} "
                  f"is set: rejecting admin request")
        admin_ns.abort(401, 'Admin authentication is not configured on this server')
    operator = _authenticated_operator()
    if not identity.has_permission(operator, permission):
        admin_ns.abort(403, f"Role '{operator.get('role')}' is not allowed to {permission}")
    return operator

# Every way of running this server imports this module — python jordan_server.py
# as much as gunicorn api:app — so this is the one place a bad setting is certain
# to be caught before the first request.
check_configuration()

#----------------------
#---MODEL DEFINITION---
#----------------------

parent_task_model = api.model('Task', {
    'taskId': fields.Integer(required=True, desciption="task identifier", example=456798),
    'name': fields.String(required=True, desciption="task name", example='Loss evaluation'),
    'progress': fields.Integer(required=False, desciption="task progress from 0 to 100", example=75),
    'state': fields.String(required=False, desciption="state (e.g STARTED, PAUSED, COMPLETE, ERROR, TIME_OUT, etc.)", example='STARTED')
})

action_parameter_model = api.model('ActionParameter', {
    'name': fields.String(required=True, description='parameter name (e.g "e-mail", "threshold", etc.)', example='recipient'),
    'type': fields.String(required=True, description='parameter type ("string", "int", or "float")', example='string'),
    'mandatory': fields.Boolean(required=False, description='is the parameter mandatory ?', example=True),
    'defaultValue': fields.String(required=False, description='pre-fill field with default value', example=0.0)
})

action_definition_with_task_model = api.model('ActionDefinition', {
    'actionName' : fields.String(required=False, description='Action name', example='send_email'),
    'parameters' : fields.List(fields.Nested(action_parameter_model), required=False, description='List of parameters and their type'),
    'parentTask': fields.Nested(parent_task_model, required=False, description='quick description of the target task for this action')
})

action_definition_model = api.model('ActionDefinition', {
    'actionName' : fields.String(required=False, description='Action name', example='send_email'),
    'parameters' : fields.List(fields.Nested(action_parameter_model), required=False, description='List of parameters and their type'),
})


# https://stackoverflow.com/questions/46171375/flask-restplus-recursive-json-mapping
MAX_SUBTASK_RECURSION_NB=10
def recursive_task_model(iteration_number=MAX_SUBTASK_RECURSION_NB):
    recursive_task_mapping = {
        'taskId': fields.Integer(required=False, desciption="task identifier", example=456798),
        'name': fields.String(required=True, desciption="task name", example='Loss evaluation'),
        'progress': fields.Integer(required=False, desciption="task progress from 0 to 100", example=75),
        'state': fields.String(required=False, desciption="state (e.g RUNNING, PAUSED, COMPLETE, ERROR, TIME_OUT, etc.)", example='RUNNING'),
        'password': fields.String(required=False, description='Access password', example='pwd'),
        'actions' : fields.List(fields.Nested(action_definition_model), required=False, description='Available actions'),
    }
    if iteration_number:
        recursive_task_mapping['tasks'] = fields.List(fields.Nested(recursive_task_model(iteration_number - 1)))
    return api.model('Task' + str(iteration_number), recursive_task_mapping)
task_model = recursive_task_model()

task_created_model = api.model('TaskCreated', {
    'taskId': fields.Integer(required=True, desciption="task identifier", example=456798),
})

client_model = api.model('Client', {
    'clientId': fields.Integer(required=True, desciption="client identifier", example=123456),
    'name': fields.String(required=True, desciption="client name", example='IA Training Bot 01'),
    'state': fields.String(required=True, desciption="state (e.g REGISTERED, UNREGISTERED, COMPLETE, ERROR, TIME_OUT, etc.)", example='REGISTERED'),
    'tasks': fields.List(fields.Nested(task_model), required=True, description='Child tasks')
})

client_registration_model = api.model('ClientRegistration', {
    'name': fields.String(required=False, description='Client name', example='IA Training Bot 01'),
    'password': fields.String(required=False, description='Access password', example='pwd'),
    'actions': fields.List(fields.Nested(action_definition_model), required=False, description='Available actions')
})

client_registered_model = api.model('ClientRegistered', {
    'taskId': fields.Integer(required=True, description='Client identifier, which is the root task identifier', example=123),
    'authToken': fields.String(required=False, description='Authentication key for future calls on this client', example='f9bf78b9a18ce6d46a0cd2b0b86df9da'),
})

status_model = api.model('Status', {
    'statusId' : fields.Integer(required=False, description='status id in server database', example=123456),
    'type': fields.String(required=True, description='status type', example='general'),
    'status': fields.String(required=True, description='status content, message', example='program still running'),
    'timestamp': fields.Integer(required=True, description='Seconds since 1970/1/1', example=int(time())),
    'parentTask': fields.Nested(parent_task_model, required=False, description='quick description of the task sending this status')
})

status_sent_model = api.model('StatusSent', {
    'statusId' : fields.Integer(required=True, description='status id in server database', example=123456),
})

#https://github.com/noirbizarre/flask-restplus/issues/172#issuecomment-277033144
wildcard_fields = api.model('GenericMapping', {
    '*': fields.Wildcard(fields.String)
})

action_model = api.model('Action', {
    'actionName': fields.String(required=True, description='refers to the action definition of the same name', example='send_email'),
    'placeholders': fields.Nested(
        wildcard_fields,
            required=False, skip_none=True, description='mapping parameter_name->value_to_pass_in', example={"recipient" : "user@mail.com"}
    )
})

message_state_audit = api.model('ActionState', {
    'timestamp': fields.Integer(required=True, description='Seconds since 1970/1/1', example=time()),
    'state': fields.String(required=True, description='state enum (SERVER_RECEIVED, MESSAGE_DELIVERED, CLIENT_RECEIVED, MESSAGE_ACKNOWLEDGED, MESSAGE_PROCESSED, MESSAGE_OVERRIDDEN, ERROR_MESSAGE_NOT_DELIVERED, ERROR_CANNOT_PROCESS_MESSAGE, ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER', example='MESSAGE_DELIVERED')
})

message_model = api.model('Message', {
    'messageId': fields.Integer(required=False, description='message id in server database', example=456789),
    'author': fields.String(required=False, readonly=True, description='authenticated login of the originator of the message. Set by the server from the admin token: a value sent in the request body is ignored', example='pupu'),
    'action': fields.Nested(action_model, required=True, description='description of the action to execute by the client'),
    'parentTask': fields.Nested(parent_task_model, required=False, description='quick description of the target task for this message'),
    'audit': fields.List(fields.Nested(message_state_audit), required=False, description='previous and current message state(s) once handled by server')
})

admin_credentials_model = api.model('AdminCredentials', {
    'login': fields.String(required=True, description='operator login declared in JORDAN_ADMIN_USERS', example='alice'),
    'password': fields.String(required=True, description='operator password', example='s3cret'),
})

admin_identity_model = api.model('AdminIdentity', {
    'login': fields.String(required=True, description='authenticated operator', example='alice'),
    'role': fields.String(required=True, description=f"one of {', '.join(identity.ROLE_PERMISSIONS)}", example=identity.ROLE_OPERATOR),
    'permissions': fields.List(fields.String, required=True, description='what this role is allowed to do', example=[identity.PERMISSION_READ, identity.PERMISSION_SEND]),
})

admin_session_model = api.inherit('AdminSession', admin_identity_model, {
    'token': fields.String(required=True, description='session token to send as Authorization: Bearer <token>', example='f9bf78b9a18ce6d46a0cd2b0b86df9da'),
    'expiresAt': fields.Integer(required=True, description='Seconds since 1970/1/1 after which the token is refused', example=int(time()) + JORDAN_DEFAULT_ADMIN_SESSION_TTL),
})

#--------------------
#---API ENDPOINTS----
#--------------------
@api.route('/hello')
class HelloWorld(Resource):
    def get(self):
        return "Hello World " + str(time())

@admin_ns.route('/hello')
class HelloAdmin(Resource):
    def get(self):
        return {'test': 'success', 'timestamp': int(time())}, 200


@client_ns.route('/register')
class Register(Resource):

    @client_ns.doc(description="Register Passive Client to the server. Open by default; when the "
                               "server sets JORDAN_REGISTRATION_KEY, one of the keys it declares "
                               "must be sent as 'Authorization: Bearer <key>'. Attempts are "
                               "rate-limited per caller address.",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'Registered',
                              401: 'registration key missing or invalid',
                              429: 'too many registration attempts'})
    @client_ns.expect(client_registration_model)
    @client_ns.marshal_with(client_registered_model)
    def post(self):
        # both guards abort with an HTTPException, so they must stay out of the
        # try block below, which would turn it into a 500
        _enforce_registration_rate_limit()
        _require_registration_key()
        try:
            client_registered = register_client(api.payload)
            return client_registered, 200
        except Exception:
            client_ns.abort(500, 'Could not register client')


@client_ns.route('/<int:parent_task_id>/task')
@client_ns.param('parent_task_id', 'The parent task identifier (may be client_id which is root task id)', default=123)
class NewTask(Resource):

    @client_ns.doc(description="Create a new task, can be see as a process.",
                   security=BEARER_AUTHORIZATION,
                   responses={201: 'Task created',
                              401: 'client token missing or invalid'})
    @client_ns.expect(task_model)
    @client_ns.marshal_with(task_created_model)
    def post(self, parent_task_id):
        _require_client_auth(parent_task_id)
        try:
            created_task = create_task(parent_task_id, api.payload)
            return created_task, 201
        except Exception:
            client_ns.abort(500, 'Could not create task')

@client_ns.route('/<int:task_id>/<string:task_state>')
@client_ns.param('task_id', 'The task identifier', default=123)
@client_ns.param('task_state', 'The new state', default="COMPLETE")
class UpdateTaskState(Resource):

    @client_ns.doc(description="Update the task state",
                   security=BEARER_AUTHORIZATION,
                   responses={202: 'Update is valid',
                              400: 'Update is invalid',
                              401: 'client token missing or invalid'})
    def put(self, task_id, task_state):
        _require_client_auth(task_id)
        try:
            update_valid = update_task(task_id, task_state)
            return None, 202 if update_valid else 400
        except Exception:
            client_ns.abort(500, 'Could not update state')

@client_ns.route('/<int:task_id>/status')
@client_ns.param('task_id', 'The task identifier', default=123)
class SendStatus(Resource):

    @client_ns.doc(description="Send a Status (may be considered as a log) of the Client to the Server",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'Status sent',
                              401: 'client token missing or invalid'})
    @client_ns.expect(status_model)
    @client_ns.marshal_with(status_sent_model)
    def post(self, task_id):
        _require_client_auth(task_id)
        try:
            status_sent = post_status(task_id, api.payload)
            return status_sent, 200
        except Exception:
            client_ns.abort(500, 'Could not receive status')

@client_ns.route('/<int:task_id>/message')
@client_ns.param('task_id', 'The task identifier', default=123)
class ReadMessage(Resource):

    @client_ns.doc(description="Get the message ordered by admin, if any",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'there is a message',
                              204: 'no message to read',
                              401: 'client token missing or invalid'})
    @client_ns.marshal_with(message_model)
    def get(self, task_id):
        _require_client_auth(task_id)
        try:
            message = read_message(task_id)
            return message, 200 if message is not None else 204
        except Exception:
            client_ns.abort(500, 'Could not access to any message')

@client_ns.route('/<int:task_id>/<int:message_id>/<string:message_state>')
@client_ns.param('task_id', 'The task identifier', default=123)
@client_ns.param('message_id', 'The message to update', default=123456)
@client_ns.param('message_state', 'The new state', default="SERVER_RECEIVED")
class UpdateMessageState(Resource):

    @client_ns.doc(description="Update the message state",
                   security=BEARER_AUTHORIZATION,
                   responses={202: 'Update is valid',
                              400: 'Update is invalid',
                              401: 'client token missing or invalid'})
    def put(self, task_id, message_id, message_state):
        _require_client_auth(task_id)
        try:
            update_valid = update_message(task_id, message_id, message_state)
            return None, 202 if update_valid else 400
        except Exception:
            client_ns.abort(500, 'Could not update state')

@client_ns.route('/<int:client_id>/unregister')
@client_ns.param('client_id', 'The client identifier', default=123)
class Unregister(Resource):

    @client_ns.doc(description="Unregister Client, ends connections",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'Unregistered',
                              400: 'client_id invalid',
                              401: 'client token missing or invalid'})
    def post(self, client_id):
        _require_client_auth(client_id)
        try:
            valid_unregister = unregister(client_id)
            return None, 200 if valid_unregister else 400
        except Exception:
            client_ns.abort(500, 'Could not update state')



@admin_ns.route('/login')
class AdminLogin(Resource):

    @admin_ns.doc(description="Exchange operator credentials for a time-limited session token, "
                              "to be sent as 'Authorization: Bearer <token>' on admin calls",
                  responses={200: 'Session opened',
                             401: 'unknown login or wrong password'})
    @admin_ns.expect(admin_credentials_model)
    @admin_ns.marshal_with(admin_session_model)
    def post(self):
        payload = api.payload or {}
        login = payload.get('login')
        try:
            operator = identity.authenticate(login, payload.get('password'))
        except ConfigurationError as unusable:
            log.error(f"Refusing every admin login: {unusable}")
            operator = None
        if operator is None:
            log.error(f"Rejected admin login attempt for '{login}'")
            admin_ns.abort(401, 'Invalid login or password')
        token = token_hex(32)
        ttl = identity.session_ttl()
        operator['expiresAt'] = int(time()) + ttl
        try:
            store_admin_session(token, operator, ttl)
        except Exception:
            admin_ns.abort(500, 'Could not open session')
        log.info(f"Admin session opened for '{login}' ({operator['role']})")
        return dict(operator, token=token), 200


@admin_ns.route('/logout')
class AdminLogout(Resource):

    @admin_ns.doc(description="Close the session token used for this call",
                  security=BEARER_AUTHORIZATION,
                  responses={200: 'Session closed',
                             401: 'admin token missing or invalid'})
    def post(self):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            # the shared bootstrap token has no session to close: nothing to delete
            delete_admin_session(_bearer_token(admin_ns))
            return None, 200
        except Exception:
            admin_ns.abort(500, 'Could not close session')


@admin_ns.route('/me')
class AdminMe(Resource):

    @admin_ns.doc(description="Identity and permissions behind the token used for this call",
                  security=BEARER_AUTHORIZATION,
                  responses={200: 'current identity',
                             401: 'admin token missing or invalid'})
    @admin_ns.marshal_with(admin_identity_model)
    def get(self):
        return _require_admin_auth(identity.PERMISSION_READ), 200


@admin_ns.route('/clients')
class ListClients(Resource):

    @admin_ns.doc(description="Get all clients and tasks available for the admin role",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'list of clients',
                              401: 'admin token missing or invalid',
                              403: 'role not allowed to read'})
    @admin_ns.marshal_with(client_model, as_list=True)
    def get(self):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            client_list = list_clients(None)
            return client_list, 200
        except Exception:
            admin_ns.abort(500, 'Could not access to any client')

@admin_ns.route('/<int:task_id>/actions')
@admin_ns.param('task_id', 'The task identifier', default=123)
class ListActions(Resource):

    @admin_ns.doc(description="Get all actions available for the admin role under this task_id/client_id",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'list of available actions',
                              401: 'admin token missing or invalid',
                              403: 'role not allowed to read'})
    @admin_ns.marshal_with(action_definition_with_task_model, as_list=True)
    def get(self, task_id):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            actions_list = list_actions(task_id, None)
            return actions_list, 200
        except Exception:
            admin_ns.abort(500, 'Could not access to any client')

@admin_ns.route('/<int:task_id>/status/<int:line_count>')
@admin_ns.param('task_id', 'The task identifier', default=123)
@admin_ns.param('line_count', 'max number of status to return (history depth)', default=10)
class ReadStatus(Resource):

    @admin_ns.doc(description="Get last statuses sent by the task",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'list of statuses',
                              204: 'no status to read',
                              401: 'admin token missing or invalid',
                              403: 'role not allowed to read'})
    @admin_ns.marshal_with(status_model, as_list=True)
    def get(self, task_id, line_count):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            status_list = read_status(task_id, line_count)
            return status_list, 200 if len(status_list) > 0 else 204
        except Exception:
            admin_ns.abort(500, 'Could not read any status')

@admin_ns.route('/<int:task_id>/message')
@admin_ns.param('task_id', 'The task identifier', default=123)
class SendMessage(Resource):

    @admin_ns.doc(description="Send a message (may be considered as a command) to the Client via the Server",
                   security=BEARER_AUTHORIZATION,
                   responses={201: 'Message sent',
                              401: 'admin token missing or invalid',
                              403: 'role not allowed to send'})
    @admin_ns.expect(message_model)
    def post(self, task_id):
        operator = _require_admin_auth(identity.PERMISSION_SEND)
        try:
            payload = dict(api.payload or {})
            # the authenticated identity is the author, whatever the body claims
            payload['author'] = operator['login']
            message_id = post_message(task_id, payload)
            return message_id, 201
        except Exception:
            admin_ns.abort(500, 'Could not receive message')

@admin_ns.route('/<int:task_id>/messages')
@admin_ns.param('task_id', 'The task identifier', default=123)
class ReadMessages(Resource):

    @admin_ns.doc(description="Get all messages sent to the task",
                   security=BEARER_AUTHORIZATION,
                   responses={200: 'list of messages',
                              204: 'no message to read',
                              401: 'admin token missing or invalid',
                              403: 'role not allowed to read'})
    @admin_ns.marshal_with(message_model, as_list=True)
    def get(self, task_id):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            message_list = list_messages(task_id)
            return message_list, 200 if len(message_list) > 0 else 204
        except Exception:
            admin_ns.abort(500, 'Could not access to any message')


@admin_ns.route('/<int:task_id>')
@admin_ns.param('task_id', 'The task identifier', default=123)
class DeleteTask(Resource):

    @admin_ns.doc(description='Delete task or client',
                  security=BEARER_AUTHORIZATION,
                  responses={200: 'task/client is deleted',
                             401: 'admin token missing or invalid',
                             403: 'role not allowed to delete'})
    def delete(self, task_id):
        _require_admin_auth(identity.PERMISSION_DELETE)
        try:
            valid_deletion = delete_task(task_id)
            return None, 200 if valid_deletion else 400
        except Exception:
            admin_ns.abort(500, 'Could not delete client')


@admin_ns.route('/all')
class DeleteAll(Resource):

    @admin_ns.doc(description='Delete everything',
                  security=BEARER_AUTHORIZATION,
                  responses={200: 'everything is deleted',
                             401: 'admin token missing or invalid',
                             403: 'role not allowed to delete'})
    def delete(self):
        _require_admin_auth(identity.PERMISSION_DELETE)
        try:
            valid_deletion = delete_all(None)#api.payload)
            return None, 200 if valid_deletion else 400
        except Exception:
            admin_ns.abort(500, 'Could not delete base')


@admin_ns.route('/<int:generic_id>')
@admin_ns.param('generic_id', 'Any id (task, status, message)', default=123)
class GenericQuery(Resource):

    @admin_ns.doc(description='Return object identified by generic_id in json format',
                  security=BEARER_AUTHORIZATION,
                  responses={200: 'Object found and returned', 204: 'ID not found',
                             401: 'admin token missing or invalid',
                             403: 'role not allowed to read'})
    def get(self, generic_id):
        _require_admin_auth(identity.PERMISSION_READ)
        try:
            serialized_object = generic_query(generic_id)
            return (serialized_object, 200) if serialized_object else ('No result', 204)
        except Exception:
            admin_ns.abort(500, 'Could not execute generic query')

def start_api():
    #about starting twice : https://stackoverflow.com/questions/9449101/how-to-stop-flask-from-initialising-twice-in-debug-mode
    log.info(f"Swagger UI available on {JORDAN_OPEN_API_URL}")
    # check_configuration() ran at import: both declarations below are usable
    operators = identity.load_operators()
    if operators:
        log.info(f"{len(operators)} admin operator account(s) declared in {JORDAN_ADMIN_USERS_ENV_VAR}")
    elif not admin_token():
        log.error(f"Neither {JORDAN_ADMIN_TOKEN_ENV_VAR} nor {JORDAN_ADMIN_USERS_ENV_VAR} is set: "
                  f"every /jordan/admin/* request will be rejected with 401")
    # check_configuration() ran at import: the keys are usable or we never got here
    keys = registration_keys()
    if keys is None:
        log.info(f"{JORDAN_REGISTRATION_KEY_ENV_VAR} is not set: anyone reaching this server can "
                 f"register a passive client")
    else:
        log.info(f"{len(keys)} registration key(s) accepted: {', '.join(name for name, _ in keys)}")
    app.run(host=JORDAN_API_HOST, port=JORDAN_API_PORT, debug=True, use_reloader=False)
