# from threading import Thread
import server.api as jordan_api

def start_api():
    print("Starting API")
    jordan_api.start_api()


if __name__ == '__main__':
    #start API
    # Thread(target=start_api).start()
    start_api() #signals work on main thread only
