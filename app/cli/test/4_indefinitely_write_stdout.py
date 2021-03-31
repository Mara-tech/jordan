from time import sleep, time
import sys

while 1:
    sys.stdout.write(f"Hello Jordan {int(time())}\n")
    sys.stdout.flush()
    sleep(1)
