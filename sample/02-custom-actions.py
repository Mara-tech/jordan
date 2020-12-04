import time
import libraries.python.jordan as jordan


JORDAN_SERVER_BASE_URL = 'http://192.168.1.41:5000/jordan/'

loop_size = 5


actions = jordan\
    .with_action('send_email')\
        .with_parameter('recipient')\
    .with_action('break_loop')\
    .with_action('shoot')\
        .with_parameter('player_name', jordan.PARAMETER_TYPE_STRING)\
        .with_parameter('points', jordan.PARAMETER_TYPE_INT)\
    .build()

jordan_instance = jordan.register(JORDAN_SERVER_BASE_URL, client_name='NBA', actions=actions, password='M1cha3l')


def do_send_email(recipient):
    print(f"Sending email to {recipient}...")


def do_shoot(player_name, score_point):
    print(f"Player {player_name} score {score_point} points !!")

for i in range(loop_size):

    time.sleep(1)

    iteration_msg = f"Iteration {i}/{loop_size}"
    jordan_instance.send_status(iteration_msg)
    print(iteration_msg)

    msg = jordan_instance.read_message()
    if msg:
        if msg['action']['action_name'] == 'send_email':
            jordan_instance.acknowledge(msg)
            do_send_email(msg['action']['placeholders']['recipient'])
            jordan_instance.processed(msg)
        elif msg['action']['action_name'] == 'break_loop':
            jordan_instance.acknowledge(msg)
            jordan_instance.processed(msg)
            break
        elif msg['action']['action_name'] == 'shoot':
            jordan_instance.acknowledge(msg)
            do_shoot(msg['action']['placeholders']['player_name'], msg['action']['placeholders']['points'])
            jordan_instance.processed(msg)

jordan_instance.unregister()


