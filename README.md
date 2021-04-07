# Jordan project

## Objectives

- Interact with an executing program
- From anywhere

## Basics

- Three entities
1. Central server, accessible by RESTful API
2. The executing program is called Passive client
3. An Active client which may be a "admin" GUI, or some decision-making bot

### Central Server
The server is composed of :
- A Stateless API
- A database

An implementation in Python creates a Flask RESTful application, and uses one implementation to communicate with the Database.
Database may be SQL, Redis, etc., as long as an implementation called `jordan_backend.py` exists near [the server main code `app.py`](server/aws_lambda/jordan-server/server/app.py).
One may create a custom implementation, based on [`base_file.py`](server/aws_lambda/jordan-server/server/backend_impl/base_file.py).

### Passive Client
Jordan aims long-running programs (it makes no sense with short executions) written in any language.
The idea is to add as few as possible lines of codes in any program, in order to integrate Jordan.
This is the goal of [libraries](libraries) already available for some programming languages.

If integration is not possible (i.e code cannot be changed), one may be interested in [Jordan-CLI](app/cli).

### Active Client
A basic use-case of an active client is someone checking the status of long-running programs from a smartphone.
Such an app is already available for [Android](app/android).
Since one of the main objectives of the Jordan project is to interact with a program,
the app GUI allows one to send messages (say command) and can then control remotely the program, 
therefore giving some sort of a generic Smartphone app for any program.

Advanced use-cases examples may include Bot (as a watcher) 
which takes decision according to the program status (kind of logs),
interacting with the program.

## Requirements
### Central server
    pip install Werkzeug==0.16.1
    pip install flask-restplus
    pip install flask-lambda-python36
    pip install redis
    pip install rejson

### Python library
    pip install jordan_py
    
Usage :
    
    from jordan_py import jordan
    
Refer to specific [README](libraries/python/jordan_py/README.md) for usage.