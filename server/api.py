from server.jordan_constants import *
import server.jordan_log as log

from server.mock import *

from flask import Flask
from flask_restplus import Api, Resource, fields

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

#----------------------
#---MODEL DEFINITION---
#----------------------

action_parameter_model = api.model('ActionParameter', {
    'name': fields.String(required=True, description='parameter name (e.g "e-mail", "threshold", etc.)', example='recipient'),
    'type': fields.String(required=True, description='parameter type (e.g "string", "int", "float", etc. )', example='string')
})

action_definition_model = api.model('ActionDefinition', {
    'action_name' : fields.String(required=False, description='Action name', example='send_email'),
    'parameters' : fields.List(fields.Nested(action_parameter_model), required=False, description='List of parameters and their type'),
})

task_model = api.model('task', {
    'task_id': fields.Integer(required=True, desciption="task identifier", example=456798),
    'name': fields.String(required=True, desciption="task name", example='Loss evaluation'),
    'state': fields.String(required=True, desciption="state (e.g STARTED, PAUSED, COMPLETE, ERROR, TIME_OUT, etc.)", example='STARTED'),
    'actions' : fields.List(fields.Nested(action_definition_model), required=False, description='Available actions')
})

client_model = api.model('Client', {
    'client_id': fields.Integer(required=True, desciption="client identifier", example=123456),
    'name': fields.String(required=True, desciption="client name", example='IA Training Bot 01'),
    'state': fields.String(required=True, desciption="state (e.g REGISTERED, UNREGISTERED, COMPLETE, ERROR, TIME_OUT, etc.)", example='REGISTERED'),
    'tasks': fields.List(fields.Nested(task_model), required=True)
})

client_registration_model = api.model('ClientRegistration', {
    'name': fields.String(required=False, description='Client name', example='IA Training Bot 01'),
    'password' : fields.String(required=False, description='Access password', example='pwd'),
    'actions' : fields.List(fields.Nested(action_definition_model), required=False, description='Available actions')
})

status_model = api.model('Status', {
    'status_id' : fields.Integer(required=False, description='status id in server database', example=123456),
    'type': fields.String(required=True, description='status type', example='general'),
    'status': fields.String(required=True, description='status content, message', example='program still running'),
    'timestamp': fields.Integer(required=True, description='Seconds since 1970/1/1', example=int(time()))
})

#https://github.com/noirbizarre/flask-restplus/issues/172#issuecomment-277033144
wildcard_fields = api.model('GenericMapping', {
    '*': fields.Wildcard(fields.String)
})

action_model = api.model('Action', {
    'action_name': fields.String(required=True, description='refers to the action definition of the same name', example='send_email'),
    'placeholders': fields.Nested(
        wildcard_fields,
            required=False, skip_none=True, description='mapping parameter_name->value_to_pass_in', example={"recipient" : "user@mail.com"}
    )
})

action_state = api.model('ActionState', {
    'timestamp': fields.Integer(required=True, description='Seconds since 1970/1/1', example=time()),
    'state': fields.String(required=True, description='state enum (SERVER_RECEIVED, MESSAGE_DELIVERED, MESSAGE_ACKNOWLEDGED, MESSAGE_PROCESSED,ERROR_MESSAGE_NOT_DELIVERED, ERROR_CANNOT_PROCESS_MESSAGE, ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER', example='MESSAGE_DELIVERED')
})

message_model = api.model('Message', {
    'message_id': fields.Integer(required=False, description='message id in server database', example=456789),
    'author': fields.String(required=True, description='authenticated login of the originator of the message', example='pupu'),
    'action': fields.Nested(action_model, required=True, description='description of the action to execute by the client'),
    'state_audit': fields.List(fields.Nested(action_state), required=False, description='previous and current message state(s) once handled by server')
})

#--------------------
#---API ENDPOINTS----
#--------------------
@api.route('/hello/')
class HelloWorld(Resource):
    def get(self):
        return "Hello World " + str(time())


@client_ns.route('/register/')
class Register(Resource):

    @client_ns.doc(description="Register Passive Client to the server",
                   responses={200: 'Registered'})
    @client_ns.expect(client_registration_model)
    def post(self):
        try:
            client_id = register_client(api.payload)
            return client_id, 200
        except:
            client_ns.abort(500, 'Could not register client')


@client_ns.route('/<int:task_id>/status')
@client_ns.param('task_id', 'The task identifier', default=123)
class SendStatus(Resource):

    @client_ns.doc(description="Send a Status (may be considered as a log) of the Client to the Server",
                   responses={200: 'Status sent'})
    @client_ns.expect(status_model)
    def post(self, task_id):
        try:
            status_id = post_status(task_id, api.payload)
            return status_id, 200
        except:
            client_ns.abort(500, 'Could not receive status')

@client_ns.route('/<int:task_id>/message/')
@client_ns.param('task_id', 'The task identifier', default=123)
class ReadMessage(Resource):

    @client_ns.doc(description="Get the message ordered by admin, if any",
                   responses={200: 'there is a message',
                              204: 'no message to read'})
    @client_ns.marshal_with(message_model)
    def get(self, task_id):
        try:
            message = read_message(task_id)
            return message, 200 if message is not None else 204
        except:
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
        try:
            update_valid = update_message(task_id, message_id, message_state)
            return None, 202 if update_valid else 400
        except:
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
        except:
            client_ns.abort(500, 'Could not access to any client')

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
        except:
            client_ns.abort(500, 'Could not read any status')

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
        except:
            client_ns.abort(500, 'Could not receive message')

@admin_ns.route('/<int:task_id>/messages/')
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
        except:
            client_ns.abort(500, 'Could not access to any message')


def start_api():
    #about starting twice : https://stackoverflow.com/questions/9449101/how-to-stop-flask-from-initialising-twice-in-debug-mode
    log.info(f"Swagger UI available on {JORDAN_OPEN_API_URL}")
    app.run(host=IPAddr, port=JORDAN_API_PORT, debug=True, use_reloader=False)
