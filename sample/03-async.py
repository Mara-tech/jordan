from jordan_py import jordan

JORDAN_SERVER_BASE_URL = 'http://192.168.1.41:5000/jordan/'

loop_A_size = 10
loop_B_size = 10
loop_C_size = 10


actions = jordan.with_action("DUMMY_ACTION").build()

jordan_instance = jordan.register(JORDAN_SERVER_BASE_URL, actions=actions)


def handle_message(msg):
    if msg and msg.action_name == "DUMMY_ACTION":
        msg.acknowledge()
        print(f"Message '{msg.action_name}' received and acknowledged")
        msg.processed()


for i in range(loop_A_size):
    for j in range(loop_B_size):


        for k in range(loop_C_size):
            jordan_instance.send_status(f"A_{i + 1}/{loop_A_size}  B_{j + 1}/{loop_B_size}  C_{k + 1}/{loop_C_size}", async_call=True)
            jordan_instance.read_message(async_callback=handle_message)


# time.sleep(3)
jordan_instance.unregister()



