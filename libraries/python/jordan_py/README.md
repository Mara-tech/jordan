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
                if jordan_message := jordan_instance.read_message():
                    if jordan_message.action_name == 'BREAK_LOOP':
                        break

    3. Choose the bound on each call, when 30 seconds is not the right wait

            import requests

            try:
                message = jordan_instance.read_message(timeout=5)
            except requests.exceptions.RequestException:
                message = None  # server unreachable or silent: try again next time

        Every call forwards its extra keyword arguments to `requests` — `timeout`, `verify`,
        `proxies`… — for each request it makes: `read_message` for the read *and* the
        acknowledgement of receipt it sends, `fatal` for its three requests, a message's
        `acknowledge()` / `processed()` for theirs. The asynchronous calls (`async_call` /
        `async_callback`) forward them too, onto their thread.

        A call that passes no `timeout` gets one: **30 seconds** per request, or the value of the
        environment variable `JORDAN_REQUEST_TIMEOUT` (seconds, the same variable and default as
        `jordan_cli`). `requests` has none of its own, so without it a server that accepts the
        connection and never answers would hold the call, and the program, forever. A request
        that times out raises `requests.exceptions.Timeout` — the same family as the
        `requests.exceptions.ConnectionError` an unreachable server already raises, so a loop
        that must survive the server catches `requests.exceptions.RequestException`, as above.
        `timeout=None` on a call restores the unbounded wait. A `JORDAN_REQUEST_TIMEOUT` that is
        not a number of seconds greater than 0 raises `ValueError` on the first call, before any
        request: `0` would make `requests` fail at once rather than wait forever.

        The acknowledgement of receipt is the one request that never raises: the server took the
        message off the queue when it answered the read, so raising would lose it for good. A
        message the server handed out is returned even when that acknowledgement fails —
        `message.receipt_confirmed` says whether the server confirmed it, `message.receipt_error`
        holds the request error of the last attempt, and `message.received()` sends it again.
        After a `requests.exceptions.Timeout` the outcome is unknown rather than negative: the
        server may have recorded it and only its answer was lost. `read_message(send_receipt=False)`
        leaves the acknowledgement to you, to bound it apart from the read. Only a read that
        fails raises — and then no message was handed out.

        One exception: an answer the library cannot decode — a body cut short by a proxy, not
        JSON, a field missing. That message is off the server's queue too, so `read_message`
        raises `jordan.UndecodableMessageError` (a `ValueError`) whose `body` holds the raw
        answer, and logs it at ERROR on the `jordan_py.jordan` logger first — on the asynchronous
        path, where nobody catches the exception, that log line is the trace left.

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

A progress is a percentage, from 0 to 100: `send_progress(50)`, `send_progress(100 * done / total)`
or `send_progress('50%')` all send the integer the server stores (truncated, so 99.6 reads 99).
Anything else — `'half'`, `150`, NaN — raises `ValueError` before any request.

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
