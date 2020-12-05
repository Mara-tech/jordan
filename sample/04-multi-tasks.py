import time
import libraries.python.jordan as jordan


JORDAN_SERVER_BASE_URL = 'http://192.168.1.41:5000/jordan/'

humanoid_actions = jordan.with_action('SHUTDOWN').build()
jordan_instance = jordan.register(JORDAN_SERVER_BASE_URL, client_name='humanoid', actions=humanoid_actions)

class Brain():
    def __init__(self, task):
        self.task = task

    def loop_iteration(self):
        brain_message = self.task.read_message(timeout=2)
        if brain_message:
            if brain_message.action_name == 'THINK':
                print(f"I'm thinking about {brain_message.placeholders.subject}")
            elif brain_message.action_name == 'IDLE':
                print(f"I'm emptying my mind")

brain_actions = jordan\
                .with_action('THINK')\
                    .with_parameter('subject')\
                    .with_parameter('duration', jordan.PARAMETER_TYPE_INT)\
                .with_action('IDLE')\
                .build()
brain_task = jordan_instance.create_task('brain', brain_actions)
brain = Brain(brain_task)


class Motor():
    def __init__(self, task):
        self.task = task

    def loop_iteration(self):
        motor_message = self.task.read_message(timeout=2)
        if motor_message:
            if motor_message.action_name == 'WALK':
                print(
                    f"I'm heading toward {motor_message.placeholders.direction} at speed {motor_message.placeholders.speed}")
            elif motor_message.action_name == 'IDLE':
                print(f"I'm good where I am")

motor_actions = jordan\
                .with_action('WALK')\
                    .with_parameter('direction', jordan.PARAMETER_TYPE_FLOAT)\
                    .with_parameter('speed', jordan.PARAMETER_TYPE_FLOAT)\
                .with_action('IDLE')\
                .build()
motor_task = jordan_instance.create_task('legs', motor_actions)
motor = Motor(motor_task)

class Sensor():
    LIGHT_VISION = 'LIGHT_VISION'
    NIGHT_VISION = 'NIGHT_VISION'
    XRAY_VISION = 'XRAY_VISION'
    VISIONS = [LIGHT_VISION, NIGHT_VISION, XRAY_VISION]

    def __init__(self, task, default_vision=LIGHT_VISION):
        self.task = task
        self.current_vision = default_vision

    def loop_iteration(self):
        sensor_message = self.task.read_message(timeout=2)
        if sensor_message:
            if sensor_message.action_name in Sensor.VISIONS:
                self.current_vision = sensor_message.action_name
        self.tell_what_you_see()


    def tell_what_you_see(self):
        self.task.send_status(f"With {self.current_vision}, I see a silhouette of a man.", timeout=2)

sensor_actions = jordan \
                .with_action(Sensor.LIGHT_VISION)\
                .with_action(Sensor.NIGHT_VISION) \
                .with_action(Sensor.XRAY_VISION)\
                .build()
sensor_task = jordan_instance.create_task('eyes', sensor_actions)
sensor = Sensor(sensor_task)




while(True):

    global_message = jordan_instance.read_message()
    if global_message:
        if global_message.action_name == 'SHUTDOWN':
            break
    brain.loop_iteration()
    motor.loop_iteration()
    sensor.loop_iteration()

jordan_instance.unregister()

