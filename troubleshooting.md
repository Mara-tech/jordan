# Common errors
## Server
### Cannot start Flask API

**Message** cannot import name 'cached_property' from 'werkzeug'

**Resolution** If Werkzeug is installed with version 1.0.0, then downgrade to version 0.16.1

    pip install Werkzeug==0.16.1