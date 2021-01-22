from rejson import Client, Path
import server.jordan_log as log
import random
import time
from secrets import token_hex


rj = Client(host='redis-12323.c56.east-us.azure.cloud.redislabs.com', port=12323, decode_responses=True,
                 db=0, password='***', socket_timeout=None)

#Jordan keywords
TASK_ACTIONS = 'actions'
PARENT_TASK = 'parentTask'
SUB_TASKS = 'tasks'
STATUS_TYPE_PROGRESS = 'progress'

#Redis keys
CLIENT_SET = 'clients'
TASK_STATUS_LIST = '{}_status'
TASK_MESSAGE_SLOT = '{}_message'
TASK_ALL_MESSAGES_LIST = '{}_all_messages'

MESSAGE_STATE_SERVER_RECEIVED = 'SERVER_RECEIVED'
MESSAGE_STATE_MESSAGE_DELIVERED = 'MESSAGE_DELIVERED'
MESSAGE_STATE_CLIENT_RECEIVED = 'CLIENT_RECEIVED'
MESSAGE_STATE_MESSAGE_ACKNOWLEDGED = 'MESSAGE_ACKNOWLEDGED'
MESSAGE_STATE_MESSAGE_PROCESSED = 'MESSAGE_PROCESSED'
MESSAGE_STATE_MESSAGE_OVERRIDDEN = 'MESSAGE_OVERRIDDEN'
MESSAGE_STATE_ERROR_MESSAGE_NOT_DELIVERED = 'ERROR_MESSAGE_NOT_DELIVERED'
MESSAGE_STATE_ERROR_CANNOT_PROCESS_MESSAGE = 'ERROR_CANNOT_PROCESS_MESSAGE'
MESSAGE_STATE_ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER = 'ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER'
MessageStateEnum = [
    MESSAGE_STATE_SERVER_RECEIVED,
    MESSAGE_STATE_MESSAGE_DELIVERED,
    MESSAGE_STATE_CLIENT_RECEIVED,
    MESSAGE_STATE_MESSAGE_ACKNOWLEDGED,
    MESSAGE_STATE_MESSAGE_PROCESSED,
    MESSAGE_STATE_MESSAGE_OVERRIDDEN,
    MESSAGE_STATE_ERROR_MESSAGE_NOT_DELIVERED,
    MESSAGE_STATE_ERROR_CANNOT_PROCESS_MESSAGE,
    MESSAGE_STATE_ERROR_MESSAGE_NOT_RECEIVED_BY_SERVER
    ]

CLIENT_STATE_REGISTERED = 'REGISTERED'
CLIENT_STATE_UNREGISTERED = 'UNREGISTERED'

TASK_STATE_STARTED = 'STARTED'
TASK_STATE_RUNNING = 'RUNNING'
TASK_STATE_PAUSED = 'PAUSED'
TASK_STATE_COMPLETE = 'COMPLETE'
TASK_STATE_ERROR = 'ERROR'
TASK_STATE_TIME_OUT = 'TIME_OUT'
TaskStateEnum = [
    TASK_STATE_STARTED,
    TASK_STATE_RUNNING,
    TASK_STATE_PAUSED,
    TASK_STATE_COMPLETE,
    TASK_STATE_ERROR,
    TASK_STATE_TIME_OUT
    ]

# obj = {
#        'answer': 42,
#        'arr': [None, True, 3.14],
#        'truth': {
#            'coord': 'out there'
#        }
#    }
# rj.jsonset('obj', Path.rootPath(), obj)
#
# # Get something
# print('Is there anybody... {}?'.format(
#    rj.jsonget('obj', Path('.truth.coord'))
# ))

def log_redis_op(msg):
    log.info('[REDIS] ' + msg)


def random_int(_from, _to):
    return random.randint(_from, _to)


def is_valid_id(key):
    return key is not None and not rj.exists(key)


def generate_client_id():
    return generate_task_id()


def generate_message_id(id=None):
    if is_valid_id(id):
        return id
    return generate_message_id(random_int(1001, 9999))

def generate_status_id(id=None):
    if is_valid_id(id):
        return id
    return generate_status_id(random_int(10000, 100000))


def generate_task_id(id=None):
    if is_valid_id(id):
        return id
    return generate_task_id(random_int(0, 1000))


def add_client(client_id, payload, token):
    payload['clientId'] = client_id
    payload['taskId'] = client_id
    payload['authToken'] = token
    payload[SUB_TASKS] = []
    payload['state'] = CLIENT_STATE_REGISTERED
    rp = rj.pipeline()
    rp.jsonset(client_id, Path.rootPath(), payload)
    rp.sadd(CLIENT_SET, client_id)
    rp.execute()


def register_client(payload):
    client_id = generate_client_id()
    token = token_hex()
    log_redis_op(f"register {str(payload)}. id={client_id}")
    add_client(client_id, payload, token)
    return {'auth_token':token, 'taskId':client_id}


def create_task(parent_task_id, payload):
    task_id = generate_task_id()
    rp = rj.pipeline()
    payload['taskId'] = task_id
    payload['parentTaskId'] = parent_task_id
    payload[SUB_TASKS] = []
    payload['state'] = TASK_STATE_STARTED
    rp.jsonarrappend(parent_task_id, Path(f'.{SUB_TASKS}'), task_id)
    rp.jsonset(task_id, Path.rootPath(), payload)
    rp.execute()
    log_redis_op(f"New task {task_id}:{payload['name']} created from task {parent_task_id}")
    return {'taskId':task_id}


def list_clients(role_payload):
    log_redis_op("list clients")
    client_ids = rj.smembers(CLIENT_SET)
    clients = rj.jsonmget(Path.rootPath(), *client_ids)
    for c in clients:
        sub_tasks = rj.jsonmget(Path.rootPath(), *c[SUB_TASKS])
        c[SUB_TASKS] = sub_tasks
    return clients


def append_actions_from_task(actions, task):
    if task is None:
        return
    parent_task = as_parent_task(task=task)
    if TASK_ACTIONS in task:
        for taskAction in task[TASK_ACTIONS]:
            taskAction[PARENT_TASK] = parent_task
            actions.append(taskAction)
    if SUB_TASKS in task:
        for sub_task_id in task[SUB_TASKS]:
            sub_task = rj.jsonget(sub_task_id)
            append_actions_from_task(actions, sub_task)


def list_actions(task_id, role_payload):
    log_redis_op(f"list actions for task {task_id}")
    task = rj.jsonget(task_id)
    actions = []
    append_actions_from_task(actions, task)
    return actions


def as_parent_task(task_id=None, task=None):
    if (task_id is None and task is None) or (task_id is not None and task is not None):
        raise ValueError('Pass either task_id or task to get parentTask info')
    if task_id is not None:
        parent_task = rj.jsonget(task_id)
    if task is not None:
        parent_task = task.copy()
    parent_task.pop('authToken', None)
    parent_task.pop('actions', None)
    parent_task.pop('clientId', None)
    parent_task.pop('tasks', None)
    return parent_task


def push_status_to_parent_tasks_list(pipeline, task_id, status_id):
    pipeline.lpush(TASK_STATUS_LIST.format(task_id), status_id)
    task = rj.jsonget(task_id)
    if 'parentTaskId' in task and task['parentTaskId'] is not None:
        push_status_to_parent_tasks_list(pipeline, task['parentTaskId'], status_id)


def push_message_to_parent_tasks_list(pipeline, task_id, message_id):
    pipeline.lpush(TASK_ALL_MESSAGES_LIST.format(task_id), message_id)
    task = rj.jsonget(task_id)
    if 'parentTaskId' in task and task['parentTaskId'] is not None:
        push_message_to_parent_tasks_list(pipeline, task['parentTaskId'], message_id)


def post_status(task_id, payload):
    log_redis_op(f"post status for task {task_id} : {payload}")
    status_id = generate_status_id()
    payload['statusId'] = status_id
    payload[PARENT_TASK] = as_parent_task(task_id=task_id)
    rp = rj.pipeline()
    push_status_to_parent_tasks_list(rp, task_id, status_id)
    rp.jsonset(status_id, Path.rootPath(), payload)
    if payload['type'] == STATUS_TYPE_PROGRESS and type(payload['status']) is int:
        rp.jsonset(task_id, Path('.progress'), payload['status'])
        rp.jsonset(task_id, Path('.state'), TASK_STATE_RUNNING)
    rp.execute()
    return {'statusId': status_id}


def read_status(task_id, line_count):
    log_redis_op(f"read {line_count} status(es) for task {task_id} and children")
    keys = rj.lrange(TASK_STATUS_LIST.format(task_id), 0, line_count)
    return rj.jsonmget(Path.rootPath(), *keys)


def create_message_audit(state):
    return {'state': state, 'timestamp':time.time()}


def init_message_audit():
    return [create_message_audit('SERVER_RECEIVED')]


def post_message(task_id, payload):
    log_redis_op(f"post message for task {task_id} : " + str(payload))
    message_id = generate_message_id()
    payload['messageId'] = message_id
    parent_task = as_parent_task(task_id=task_id)
    payload[PARENT_TASK] = parent_task
    payload['audit'] = init_message_audit()
    rp = rj.pipeline()
    rp.set(TASK_MESSAGE_SLOT.format(task_id), message_id)
    push_message_to_parent_tasks_list(rp, task_id, message_id)
    rp.jsonset(message_id, Path.rootPath(), payload)
    rp.execute()
    return message_id


def update_message(task_id, message_id, message_state):
    log_redis_op(f"update message {message_id} for task {task_id}. New state : {message_state}")
    if message_state not in MessageStateEnum:
        return False
    return rj.jsonarrappend(message_id, '.audit', create_message_audit(message_state))

def update_task(task_id, task_state):
    log_redis_op(f"update task {task_id}. New state : {task_state}")
    if task_state not in TaskStateEnum:
        return False
    return set_task_state(task_id, task_state)


def read_message(task_id):
    log_redis_op(f"read message for task {task_id}")
    task_message_slot = TASK_MESSAGE_SLOT.format(task_id)
    message_to_read_id = rj.get(task_message_slot)
    if message_to_read_id:
        rj.delete(task_message_slot)
        update_message(task_id, message_to_read_id, 'MESSAGE_DELIVERED') #may be async
        return rj.jsonget(message_to_read_id) #use pipeline ?
    else:
        # no message to read, which is very normal behavior
        return None


def list_messages(task_id):
    log_redis_op(f"read message(s) for task {task_id}" )
    keys = rj.lrange(TASK_ALL_MESSAGES_LIST.format(task_id), 0, -1)
    return rj.jsonmget(Path.rootPath(), *keys)


def set_task_state(task_id, state):
    return rj.jsonset(task_id, Path('.state'), state)


def unregister(client_id):
    log_redis_op(f"unregister client {client_id}")
    return set_task_state(client_id, CLIENT_STATE_UNREGISTERED)