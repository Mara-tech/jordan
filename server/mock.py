import server.jordan_log as log
import random
from time import time
from secrets import token_hex
def mock_log(msg):
    log.info('[MOCK] ' + msg)


def random_int(_from, _to):
    return random.randint(_from, _to)

def generate_client_id():
    return random_int(0, 100)

def generate_message_id():
    return random_int(0, 100000)

def generate_status_id():
    return random_int(0, 10000)

def generate_task_id():
    return random_int(0, 1000)


def register_client(payload):
    client_id = generate_client_id()
    token = token_hex()
    default_task_id = generate_task_id()
    mock_log(f"register {str(payload)}. id={client_id}")
    return {'client_id':client_id, 'auth_token':token, 'default_task_id':default_task_id}

def list_clients(authentication_payload):
    mock_log("list clients")

    parameters_client_1 = {
        'name': 'recipient',
        'type': 'string'
    }
    actions_client_1 = {
        'action_name' : 'send_email',
        'parameters' : [parameters_client_1],
    }
    task_client_1 = {
        'task_id': 456798,
        'name': 'Loss evaluation',
        'state': 'STARTED',
        'actions' : [actions_client_1]
    }
    client_1 = {
        'client_id': 123456,
        'name': 'IA Training Bot 01',
        'state': 'REGISTERED',
        'tasks': [task_client_1]
    }



    parameter1_action_t1_c2 = {
        'name': 'body_part',
        'type': 'string'
    }
    parameter2_action_t1_c2 = {
        'name': 'side',
        'type': 'string'
    }
    action_1_t1_c2 = {
        'action_name': 'goal',
        'parameters': [parameter1_action_t1_c2,parameter2_action_t1_c2],
    }
    action_2_t1_c2 = {
        'action_name': 'spike',
    }
    task_1_client_2 = {
        'task_id': 16462,
        'name': 'General',
        'state': 'STARTED',
        'actions': [action_1_t1_c2, action_2_t1_c2]
    }
    task_2_client_2 = {
        'task_id': 16462,
        'name': 'Logging',
        'state': 'STARTED'
    }
    client_2 = {
        'client_id': 165744,
        'name': 'IA Training Bot 02',
        'state': 'TIME_OUT',
        'tasks': [task_1_client_2,task_2_client_2]
    }

    client_list = [client_1, client_2]
    return client_list

def post_status(task_id, payload):
    mock_log("post status " + str(payload))
    status_id = generate_status_id()
    return {'status_id': status_id}

def read_status(task_id, line_count):
    mock_log(f"read {line_count} status(es) for task {task_id}" )
    mock_status_1 = {"status_id" : 46846, "type":"success", "status":"program done"}
    mock_status_2 = {"type":"progress", "status":"77%", "timestamp":1607005146, "should_not_appear":"KO"}
    return [mock_status_1, mock_status_2]


def post_message(task_id, payload):
    mock_log(f"post message for task {task_id} : " + str(payload))
    message_id = generate_message_id()
    return message_id

MessageStateEnum = [
    'SERVER_RECEIVED',
    'MESSAGE_DELIVERED',
    'MESSAGE_ACKNOWLEDGED',
    'MESSAGE_PROCESSED',
    'ERROR_MESSAGE_NOT_DELIVERED',
    'ERROR_CANNOT_PROCESS_MESSAGE',
    'ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER'
    ]

def update_message(task_id, message_id, message_state):
    mock_log(f"update message {message_id} for task {task_id}. New state : {message_state}")
    return message_state in MessageStateEnum



def list_messages(task_id):
    mock_log(f"read message(s) for task {task_id}" )
    mock_message_1 = {"message_id":generate_message_id(), "author":"cpuyol", "action":{"action_name" : "goal", "placeholders" : {"body_part":"head", "side":"opponent"}}, "state_audit":[{"timestamp":int(time()), "state":"SERVER_RECEIVED"}]}
    mock_message_2 = {"message_id":generate_message_id(), "author":"engapeth", "action":{"action_name" : "spike"}, "state_audit":[{"timestamp":int(time()), "state":"SERVER_RECEIVED"}, {"timestamp":int(time()+1), "state":"MESSAGE_DELIVERED"}, {"timestamp":int(time()+2), "state":"MESSAGE_PROCESSED"}], "france-bresil":"3-0"}
    return [mock_message_1, mock_message_2]

def read_message(task_id):
    random_switch = random_int(0,4)
    mock_log(f"read message for task {task_id} -> {'No message' if random_switch == 0 else 'Mock message '+str(random_switch)}")
    if random_switch == 1:
        mock_message_1 = {"message_id": generate_message_id(), "author": "cpuyol",
                          "action": {"action_name": "goal", "placeholders": {"body_part": "head", "side": "opponent"}},
                          "state_audit": [{"timestamp": int(time()), "state": "SERVER_RECEIVED"}]}
        return mock_message_1
    elif random_switch == 2:
        mock_message_2 = {"message_id": generate_message_id(), "author": "mjordan",
                          "action": {"action_name": "shoot", "placeholders": {"player_name": "parker", "points": 3}},
                          "state_audit": [{"timestamp": int(time()), "state": "SERVER_RECEIVED"}]}
        return mock_message_2
    elif random_switch == 3:
        mock_message_3 = {"message_id": generate_message_id(), "author": "bgates",
                          "action": {"action_name": "send_email", "placeholders": {"recipient": "contact@jordan.com"}},
                          "state_audit": [{"timestamp": int(time()), "state": "SERVER_RECEIVED"}]}
        return mock_message_3
    elif random_switch == 4:
        mock_message_3 = {"message_id": generate_message_id(), "author": "ealderson",
                          "action": {"action_name": "DUMMY_ACTION"},
                          "state_audit": [{"timestamp": int(time()), "state": "SERVER_RECEIVED"}]}
        return mock_message_3
    else:
        #empty message
        return None


def unregister(client_id):
    mock_log(f"unregister client {client_id}")
    return None