# Jordan ?

Jordan project let an executing program being interacted with, from anywhere.
Once registered to a Jordan server, a program instance becomes a Jordan client 
which may emit statuses (such as logs and progress details), and may react to some user action.
For example, a Human user may want to trigger an email sending for reporting purpose.
Actions are generic, as much as the App GUI. Therefore, you already have an App for your program !

# Get started

1. Get a working Jordan server instance

2. Import jordan

        from jordan_py import jordan

3. In your Python program, register to this server

        jordan_instance = jordan.register('<jordan_server_url>')
        
    1. Send a status
    
            jordan_instance.send_status('The program is well started.')

    2. React on received message
    
            while True:
                if jordan_message = jordan_instance.read_message():
                        if jordan_message.action_name == 'BREAK_LOOP':
                            break

# More use cases

Explore [samples](https://github.com/Mara-tech/jordan/tree/main/sample) for details on features like :
- action
- task
- status type

### Action
Has a name, and optionally (typed) (defaulted) parameters.
e.g.

            actions = jordan.with_action('send_email')
                    .with_parameter('recipient')
                    .with_parameter('delay', jordan.PARAMETER_TYPE_INT, 0)
                    .build()

            jordan_instance = jordan.register('<jordan_server_url>', 'sample-client-sending-email', actions)

### Task
Hierarchy of task/sub-task. The jordan client instance returned by `register()` function is a root task.

        meal_task = dinner_jordan_instance.create_task('meal')
        dessert_task = dinner_jordan_instance.create_task('dessert')
        meal_task.send_status('Not enough food in storage')
        dessert_task.send_status('Dessert is ready')

### Status Type
May be easier to understand and analyze statuses (in a client app which allows filtering).

        meal_task.send_failure_status('Not enough food in storage')
        dessert_task.send_status('Dessert is being prepared')
        dessert_task.send_progress(50)
        dessert_task.send_success_status('Dessert is ready')
        dessert_task.send_typed_status('eaten', 'Dessert has been eaten by Michael')