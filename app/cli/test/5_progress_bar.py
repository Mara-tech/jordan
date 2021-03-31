from tqdm import tqdm
from time import sleep

result = 0
for i in tqdm(range(10)):
    result += i
    sleep(1)