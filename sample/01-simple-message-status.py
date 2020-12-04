import time
import libraries.python.jordan as jordan


JORDAN_SERVER_BASE_URL = 'http://192.168.1.41:5000/jordan/'

loop_size = 5


actions = jordan.with_action("DUMMY_ACTION").build()

jordan_instance = jordan.register(JORDAN_SERVER_BASE_URL, actions=actions)


for i in range(loop_size):

    time.sleep(1)

    iteration_msg = f"Iteration {i}/{loop_size}"
    jordan_instance.send_status(iteration_msg)
    print(iteration_msg)

    msg = jordan_instance.read_message()
    if msg:
        jordan_instance.acknowledge(msg)
        print(f"Message '{msg['action']['action_name']}' received and acknowledged")
        jordan_instance.processed(msg)

jordan_instance.unregister()


