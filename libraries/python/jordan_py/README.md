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

4. If the server closed registration (`JORDAN_REGISTRATION_KEY` set on its side), present the key

        jordan_instance = jordan.register('<jordan_server_url>', registration_key='<key>')

    Leaving the argument out makes the library read the `JORDAN_REGISTRATION_KEY` environment
    variable instead. Without a valid key such a server answers `401`, and `429` when too many
    attempts come from the same address in a short window.

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

### Metric
A named value, which an active client (the Android app, in its *Metrics* tab) draws as a curve:
one curve per name. `step` is the progress point the value belongs to — the epoch, the
iteration; without it, the value is placed in time.

        for epoch in range(1, epochs + 1):
            train_one_epoch()
            training_task.send_metric('held-out loss', evaluate(), step=epoch)
        training_task.send_metric('throughput', items_per_second)

The value also reaches the log of statuses as a line of text (`held-out loss = 0.6648 (step 3)`).
A value that is not a finite number (NaN, infinity) is not sent — `send_metric` returns `None` —
so a diverging computation never makes the loop reporting it raise. Numpy and torch scalars are
accepted as they are. See [sample 05](https://github.com/Mara-tech/jordan/blob/main/sample/05-metrics.py).
