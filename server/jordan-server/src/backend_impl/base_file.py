
# Client endpoints


def register_client(payload, **kwargs):
    pass


def create_task(parent_task_id, payload, **kwargs):
    pass


def update_task(task_id, task_state, **kwargs):
    pass


def post_status(task_id, payload, **kwargs):
    pass


def read_message(task_id, **kwargs):
    pass


def update_message(task_id, message_id, message_state, **kwargs):
    pass


def unregister(client_id, **kwargs):
    pass


# Admin endpoints


def list_clients(payload, **kwargs):
    pass


def list_actions(task_id, payload, **kwargs):
    pass


def read_status(task_id, line_count, **kwargs):
    pass


def post_message(task_id, payload, **kwargs):
    pass


def list_messages(task_id, **kwargs):
    pass


def delete_task(task_id, payload=None, **kwargs):
    pass


def delete_all(payload=None, **kwargs):
    pass
