from jordan_constants import *
import jordan_log as log

from rejson_interface import *

from flask import Flask, request
from flask_restx import Api, Resource, fields

from time import time

# Full example : https://flask-restplus.readthedocs.io/en/stable/example.html
# --> namespace, marshall (serialize), docs, params, etc.

#--------------------
#---API DEFINITION---
#--------------------
app = Flask(__name__)
api = Api(app,
          version='1',
          title='Jordan Server API',
          description='Interactions with Jordan server',
          # license='MIT',
          # contact='Pupu',
          # contact_url='https://github.com/Mara-tech/jordan',
          doc=JORDAN_OPEN_API_DOC_SUFFIX,
          prefix=JORDAN_API_PATH_PREFIX
          )

client_ns = api.namespace('client', description='Client-side operations')
admin_ns = api.namespace('admin', description='Admin-side operations')


def _require_client_auth(task_id):
    auth = request.headers.get('Authorization', '')
    if not auth.startswith('Bearer '):
        client_ns.abort(401, 'Missing Authorization: Bearer <token> header')
    token = auth[7:]
    if not validate_auth_token(task_id, token):
        client_ns.abort(401, 'Invalid authentication token')

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
    'author': fields.String(required=True, description='authenticated login of the originator of the message', example='pupu'),
    'action': fields.Nested(action_model, required=True, description='description of the action to execute by the client'),
    'parentTask': fields.Nested(parent_task_model, required=False, description='quick description of the target task for this message'),
    'audit': fields.List(fields.Nested(message_state_audit), required=False, description='previous and current message state(s) once handled by server')
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

    @client_ns.doc(description="Register Passive Client to the server",
                   responses={200: 'Registered'})
    @client_ns.expect(client_registration_model)
    @client_ns.marshal_with(client_registered_model)
    def post(self):
        try:
            client_registered = register_client(api.payload)
            return client_registered, 200
        except Exception:
            client_ns.abort(500, 'Could not register client')


@client_ns.route('/<int:parent_task_id>/task')
@client_ns.param('parent_task_id', 'The parent task identifier (may be client_id which is root task id)', default=123)
class NewTask(Resource):

    @client_ns.doc(description="Create a new task, can be see as a process.",
                   responses={201: 'Task created'})
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
                   responses={202: 'Update is valid',
                              400: 'Update is invalid'})
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
                   responses={200: 'Status sent'})
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
                   responses={200: 'there is a message',
                              204: 'no message to read'})
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
                   responses={202: 'Update is valid',
                              400: 'Update is invalid'})
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
                   responses={200: 'Unregistered',
                              400: 'client_id invalid'})
    def post(self, client_id):
        _require_client_auth(client_id)
        try:
            valid_unregister = unregister(client_id)
            return None, 200 if valid_unregister else 400
        except Exception:
            client_ns.abort(500, 'Could not update state')



@admin_ns.route('/clients')
class ListClients(Resource):

    @admin_ns.doc(description="Get all clients and tasks available for the admin role",
                   responses={200: 'list of clients'})
    @admin_ns.marshal_with(client_model, as_list=True)
    def get(self):
        try:
            client_list = list_clients(api.authorizations)
            return client_list, 200
        except Exception:
            admin_ns.abort(500, 'Could not access to any client')

@admin_ns.route('/<int:task_id>/actions')
@admin_ns.param('task_id', 'The task identifier', default=123)
class ListActions(Resource):

    @admin_ns.doc(description="Get all actions available for the admin role under this task_id/client_id",
                   responses={200: 'list of available actions'})
    @admin_ns.marshal_with(action_definition_with_task_model, as_list=True)
    def get(self, task_id):
        try:
            actions_list = list_actions(task_id, api.authorizations)
            return actions_list, 200
        except Exception:
            admin_ns.abort(500, 'Could not access to any client')

@admin_ns.route('/<int:task_id>/status/<int:line_count>')
@admin_ns.param('task_id', 'The task identifier', default=123)
@admin_ns.param('line_count', 'max number of status to return (history depth)', default=10)
class ReadStatus(Resource):

    @admin_ns.doc(description="Get last statuses sent by the task",
                   responses={200: 'list of statuses',
                              204: 'no status to read'})
    @admin_ns.marshal_with(status_model, as_list=True)
    def get(self, task_id, line_count):
        try:
            status_list = read_status(task_id, line_count)
            return status_list, 200 if len(status_list) > 0 else 204
        except Exception:
            admin_ns.abort(500, 'Could not read any status')

@admin_ns.route('/<int:task_id>/message')
@admin_ns.param('task_id', 'The task identifier', default=123)
class SendMessage(Resource):

    @admin_ns.doc(description="Send a message (may be considered as a command) to the Client via the Server",
                   responses={201: 'Message sent'})
    @admin_ns.expect(message_model)
    def post(self, task_id):
        try:
            message_id = post_message(task_id, api.payload)
            return message_id, 201
        except Exception:
            admin_ns.abort(500, 'Could not receive message')

@admin_ns.route('/<int:task_id>/messages')
@admin_ns.param('task_id', 'The task identifier', default=123)
class ReadMessages(Resource):

    @admin_ns.doc(description="Get all messages sent to the task",
                   responses={200: 'list of messages',
                              204: 'no message to read'})
    @admin_ns.marshal_with(message_model, as_list=True)
    def get(self, task_id):
        try:
            message_list = list_messages(task_id)
            return message_list, 200 if len(message_list) > 0 else 204
        except Exception:
            admin_ns.abort(500, 'Could not access to any message')


@admin_ns.route('/<int:task_id>')
@admin_ns.param('task_id', 'The task identifier', default=123)
class DeleteTask(Resource):

    @admin_ns.doc(description='Delete task or client',
                  responses={200: 'task/client is deleted'})
    def delete(self, task_id):
        try:
            valid_deletion = delete_task(task_id)
            return None, 200 if valid_deletion else 400
        except Exception:
            admin_ns.abort(500, 'Could not delete client')


@admin_ns.route('/all')
class DeleteAll(Resource):

    @admin_ns.doc(description='Delete everything',
                  responses={200: 'everything is deleted'})
    def delete(self):
        try:
            valid_deletion = delete_all(None)#api.payload)
            return None, 200 if valid_deletion else 400
        except Exception:
            admin_ns.abort(500, 'Could not delete base')


@admin_ns.route('/<int:generic_id>')
@admin_ns.param('generic_id', 'Any id (task, status, message)', default=123)
class GenericQuery(Resource):

    @admin_ns.doc(description='Return object identified by generic_id in json format',
                  responses={200: 'Object found and returned', 204:'ID not found'})
    def get(self, generic_id):
        try:
            serialized_object = generic_query(generic_id)
            return (serialized_object, 200) if serialized_object else ('No result', 204)
        except Exception:
            admin_ns.abort(500, 'Could not execute generic query')

def start_api():
    #about starting twice : https://stackoverflow.com/questions/9449101/how-to-stop-flask-from-initialising-twice-in-debug-mode
    log.info(f"Swagger UI available on {JORDAN_OPEN_API_URL}")
    app.run(host=IPAddr, port=JORDAN_API_PORT, debug=True, use_reloader=False)
