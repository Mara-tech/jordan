import math
import random
import time
from jordan_py import jordan

JORDAN_SERVER_BASE_URL = 'http://192.168.1.41:5000/jordan/'

epochs = 24
batches_per_epoch = 20

# A training loop, simulated: each epoch is evaluated, and the values are sent as metrics.
# An active client draws one curve per metric name — the Android app in its "Metrics" tab.

actions = jordan.with_action('STOP').build()

with jordan.register(JORDAN_SERVER_BASE_URL, client_name='training', actions=actions) as jordan_instance:
    fine_tune = jordan_instance.create_task('fine_tune')

    for epoch in range(1, epochs + 1):
        start = time.time()
        for batch in range(batches_per_epoch):
            time.sleep(0.05)
        # held against its epoch: the curve can be read against steps
        training_loss = 0.60 + 0.20 * math.exp(-epoch / 4) + random.uniform(-0.005, 0.005)
        held_out_loss = 0.66 + 0.01 * math.exp(-epoch / 6) + 0.0004 * epoch + random.uniform(-0.002, 0.002)
        fine_tune.send_metric('training loss', training_loss, step=epoch)
        fine_tune.send_metric('held-out loss', held_out_loss, step=epoch)
        fine_tune.send_progress(100 * epoch / epochs)  # a percentage, from 0 to 100

        # no step: this one is placed in time
        jordan_instance.send_metric('batches per second', round(batches_per_epoch / (time.time() - start), 1))

        msg = jordan_instance.read_message()
        if msg and msg.action_name == 'STOP':
            msg.acknowledge()
            fine_tune.send_status(f'Stopped by hand after epoch {epoch}')
            msg.processed()
            break

    fine_tune.complete()
