# Jordan project

## Objectives

- Interact with an executing program
- From anywhere

## Basics

- Three entities
1. Central server, accessible by RESTful API
2. The executing program is called Passive client
3. An Active client which may be a "admin" GUI, or some decision-making bot

## Requirements
### Central server
    pip install flask-restplus
    pip install Werkzeug==0.16.1
    pip install redis
    pip install rejson

### Python library
    pip install jordan_py
    
Usage :
    
    from jordan_py import jordan
    
Refer to specific [README](libraries/python/jordan_py/README.txt) for usage.