from time import sleep
import sys


def sout(txt):
    sys.stdout.write(f'{txt}\n')
    sys.stdout.flush()


sout("Hello Jordan 1")
sleep(1)
sout("Hello Jordan 2")
sout("Hello Jordan 3")
sleep(2)
sout("Hello Jordan 4")
sleep(3)
sout("Hello Jordan 5")
sout("Hello Jordan 6")
