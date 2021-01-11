# Jordan project

## Objectives

- Interact with un executing program
- From anywhere

## Basics

- Three entities
1. Central server, accessible by RESTful API
2. The executing program is called Passive client
3. An Active client which may be a "admin" GUI, or some decision-making bot

## Requirements
### Central server
    pip install requests
    pip install flask-restplus
    pip install Werkzeug==0.16.1
    pip install redis
    pip install rejson
