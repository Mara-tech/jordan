import server.jordan_log as log
import random
import time
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
    root_task_id = generate_task_id()
    mock_log(f"register {str(payload)}. id={client_id}")
    return {'auth_token':token, 'taskId':root_task_id}

def create_task(parent_task_id, payload):
    task_id = generate_task_id()
    mock_log(f"New task {task_id}:{payload['name']} created from task {parent_task_id}")
    return {'taskId':task_id}

def list_clients(authentication_payload):
    mock_log("list clients")

    parameters_client_1 = {
        'name': 'recipient',
        'type': 'string'
    }
    actions_client_1 = {
        'actionName' : 'send_email',
        'parameters' : [parameters_client_1],
    }
    task_client_1 = {
        'taskId': 456798,
        'name': 'Loss evaluation',
        'state': 'RUNNING',
        'progress': 70,
        'actions' : [actions_client_1]
    }
    client_1 = {
        'clientId': 123456,
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
        'actionName': 'goal',
        'parameters': [parameter1_action_t1_c2,parameter2_action_t1_c2],
    }
    action_2_t1_c2 = {
        'actionName': 'spike',
    }
    task_1_client_2 = {
        'taskId': 16462,
        'name': 'General',
        'state': 'RUNNING',
        'progress': 35,
        'actions': [action_1_t1_c2, action_2_t1_c2]
    }
    task_2_client_2 = {
        'taskId': 16462,
        'name': 'Logging',
        'state': 'RUNNING'
    }
    client_2 = {
        'clientId': 165744,
        'name': 'IA Training Bot 02',
        'state': 'TIME_OUT',
        'tasks': [task_1_client_2,task_2_client_2]
    }

    client_list = [client_1, client_2]
    return client_list

def list_actions(task_id, authentication_payload):
    mock_log("list actions")

    brain_task = {
        'taskId': 1,
        'name': 'brain',
        'progress': 70
    }

    legs_task = {
        'taskId': 2,
        'name': 'legs',
        'progress': 24
    }

    eyes_task = {
        'taskId': 3,
        'name': 'eyes'
    }

    arm_task = {
        'taskId': 4,
        'name': 'arm',
    }

    action_think = {
        'actionName': 'think',
        'parameters': [
            {
                'name': 'subject',
                'type': 'string',
                'mandatory': True
            },
            {
                'name': 'duration',
                'type': 'int',
                'defaultValue': 30
            }
            ],
        'parentTask': brain_task
    }

    action_idle = {
        'actionName': 'idle',
        'parentTask': brain_task
    }

    action_walk = {
        'actionName': 'walk',
        'parameters': [
            {
                'name': 'direction',
                'type': 'float',
                'defaultValue': 180.0
            },
            {
                'name': 'speed',
                'type': 'float',
                'defaultValue': 5.0
            }
            ],
        'parentTask': legs_task
    }

    action_idle_2 = {
        'actionName': 'idle',
        'parentTask': legs_task
    }

    action_light = {
        'actionName': 'light_vision',
        'parentTask': eyes_task
    }
    action_night = {
        'actionName': 'night_vision',
        'parentTask': eyes_task
    }
    action_xray = {
        'actionName': 'xray_vision',
        'parentTask': eyes_task
    }

    action_grab = {
        'actionName': 'grab_object',
        'parameters': [
            {
                'name': 'object_name',
                'defaultValue': 'gun',
            },
            {
                'name': 'hand',
                'defaultValue': 'right'
            }
            ],
        'parentTask': arm_task
    }
    action_use = {
        'actionName': 'use_object',
        'parameters': [
            {
                'name': 'aim',
                'type': 'float',
                'defaultValue': 0.0,
            },
            {
                'name': 'hand',
                'defaultValue': 'right'
            }
            ],
        'parentTask': arm_task
    }
    action_put = {
        'actionName': 'put_object',
        'parameters': [
            {
                'name': 'hand',
                'defaultValue': 'right'
            }
            ],
        'parentTask': arm_task
    }
    actions_list = [
        action_think, action_idle, action_walk, action_idle_2, action_light, action_night, action_xray, action_grab, action_use, action_put
    ]
    return actions_list

def post_status(task_id, payload):
    mock_log("post status " + str(payload))
    status_id = generate_status_id()
    return {'statusId': status_id}

def read_status(task_id, line_count):
    mock_log(f"read {line_count} status(es) for task {task_id}" )
    brain_task = {"taskId":1, "name":"brain"}
    legs_task = {"taskId": 2, "name": "legs"}
    eyes_task = {"taskId": 3, "name": "eyes"}
    arms_task = {"taskId": 4, "name": "arms"}
    return [
        {"statusId" : 1, "type":"general", "status":"I'm starting to think about Human condition.", "parentTask": brain_task, "timestamp":1607971392285, },
        {"statusId" : 2, "type":"general", "status":"My conclusion is : Humans suck", "parentTask": brain_task, "timestamp":1607971394285, },
        {"statusId" : 3, "type":"general", "status":"I'm walking South", "parentTask": legs_task, "timestamp":1607971294285, },
        {"statusId" : 4, "type":"success", "status":"Checkpoint reached !", "parentTask": legs_task, "timestamp":1607971394285, },
        {"statusId" : 5, "type":"failure", "status":"Couldn't switch to X-RAY vision.", "parentTask": eyes_task, "timestamp":1607571294285, },
        {"statusId" : 6, "type":"general", "status":"I see a silhouette of a man.", "parentTask": eyes_task, "timestamp":time.time()*1000, }
        ]
    # mock_status_1 = {"status_id" : 1, "type":"general", "status":"I'm starting to think about Human condition.", "parentTask": brain_task, "timestamp":1607971392285, }
    # mock_status_2 = {"type":"progress", "status":"77%", "timestamp":1607005146, "should_not_appear":"KO"}
    # return [mock_status_1, mock_status_2]


def post_message(task_id, payload):
    mock_log(f"post message for task {task_id} : " + str(payload))
    message_id = generate_message_id()
    time.sleep(1)
    return message_id

MessageStateEnum = [
    'SERVER_RECEIVED',
    'MESSAGE_DELIVERED',
    'CLIENT_RECEIVED',
    'MESSAGE_ACKNOWLEDGED',
    'MESSAGE_PROCESSED',
    'MESSAGE_OVERRIDDEN',
    'ERROR_MESSAGE_NOT_DELIVERED',
    'ERROR_CANNOT_PROCESS_MESSAGE',
    'ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER'
    ]

def update_message(task_id, message_id, message_state):
    mock_log(f"update message {message_id} for task {task_id}. New state : {message_state}")
    return message_state in MessageStateEnum



def list_messages(task_id):
    mock_log(f"read message(s) for task {task_id}" )
    mock_message_1 = {"messageId":generate_message_id(), "author":"cpuyol", "action":{"actionName" : "goal", "placeholders" : {"body_part":"head", "side":"opponent"}}, "audit":[{"timestamp":int(time.time()), "state":"SERVER_RECEIVED"}]}
    mock_message_2 = {"messageId":generate_message_id(), "author":"engapeth", "action":{"actionName" : "spike"}, "audit":[{"timestamp":int(time.time()), "state":"SERVER_RECEIVED"}, {"timestamp":int(time.time()+1), "state":"MESSAGE_DELIVERED"}, {"timestamp":int(time.time()+2), "state":"MESSAGE_PROCESSED"}], "france-bresil":"3-0"}
    return [
        mock_message_1,
        mock_message_2
    ]

def read_message(task_id):
    random_switch = random_int(0,11)
    mock_log(f"read message for task {task_id} -> {'No message' if random_switch == 0 else 'Mock message '+str(random_switch)}")
    if random_switch == 1:
        mock_message = {"messageId": generate_message_id(), "author": "cpuyol",
                          "action": {"actionName": "goal", "placeholders": {"body_part": "head", "side": "opponent"}},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 2:
        mock_message = {"messageId": generate_message_id(), "author": "mjordan",
                          "action": {"actionName": "shoot", "placeholders": {"playerName": "parker", "points": 3}},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 3:
        mock_message = {"messageId": generate_message_id(), "author": "bgates",
                          "action": {"actionName": "send_email", "placeholders": {"recipient": "contact@jordan.com"}},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 4:
        mock_message = {"messageId": generate_message_id(), "author": "ealderson",
                          "action": {"actionName": "DUMMY_ACTION"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 5:
        mock_message = {"messageId": generate_message_id(), "author": "arodin",
                          "action": {"actionName": "THINK", "placeholders": {"subject": "Science", "duration":60}},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 6:
        mock_message = {"messageId": generate_message_id(), "author": "white-rabbit",
                          "action": {"actionName": "IDLE"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 7:
        mock_message = {"messageId": generate_message_id(), "author": "fgump",
                          "action": {"actionName": "WALK", "placeholders": {"direction": 270, "speed":50}},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 8:
        mock_message = {"messageId": generate_message_id(), "author": "sstrange",
                          "action": {"actionName": "LIGHT_VISION"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 9:
        mock_message = {"messageId": generate_message_id(), "author": "sfisher",
                          "action": {"actionName": "NIGHT_VISION"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 10:
        mock_message = {"messageId": generate_message_id(), "author": "jhowlett",
                          "action": {"actionName": "XRAY_VISION"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    elif random_switch == 11:
        mock_message = {"messageId": generate_message_id(), "author": "iasimov",
                          "action": {"actionName": "SHUTDOWN"},
                          "audit": [{"timestamp": int(time.time()), "state": "SERVER_RECEIVED"}]}
        return mock_message
    else:
        #empty message
        return None


def unregister(client_id):
    mock_log(f"unregister client {client_id}")
    return True