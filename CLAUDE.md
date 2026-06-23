# Jordan — Developer Guide

## Architecture

Jordan is a 3-layer system:

```
┌─────────────────────────────────────────────────────────┐
│  Active client  (admin UI, bot, human operator)         │
│  → sends messages, reads status                         │
└──────────────────────┬──────────────────────────────────┘
                       │ REST /jordan/admin/*
┌──────────────────────▼──────────────────────────────────┐
│  Central server  (server/)                              │
│  Flask-RESTX · Redis backend · port 5000                │
└──────────────────────┬──────────────────────────────────┘
                       │ REST /jordan/client/*
┌──────────────────────▼──────────────────────────────────┐
│  Passive client  (the executing program)                │
│  uses jordan_py · registers, sends status, reads msgs   │
└─────────────────────────────────────────────────────────┘
```

**Protocol source of truth:** [`libraries/prototype/contract.md`](libraries/prototype/contract.md)

---

## Project layout

```
server/             Flask-RESTX server + Redis interface
  api.py            REST endpoints (client_ns, admin_ns)
  rejson_interface.py  Redis read/write layer
  jordan_constants.py  Port, host, Redis keys
  jordan_server.py  Entry point: python jordan_server.py
  mock.py           Dev/test data fixtures
  requirements.txt  Pinned dependencies

libraries/
  prototype/contract.md   API specification (authoritative)
  python/jordan_py/       Python passive-client library (jordan_py on PyPI)
  java/class_diagram.md   Planned Java client (not yet implemented)

app/android/        Android active-client app (Gradle)

sample/             Runnable examples (numbered 01–04)
```

---

## Running the server

```bash
cd server
cp .env.example .env          # fill in Redis credentials
pip install -r requirements.txt
python jordan_server.py
```

Server listens at `http://localhost:5000/jordan`.  
Swagger UI: `http://localhost:5000/jordan/swagger-ui`

**Required environment variables** (in `server/.env`):

| Variable | Description |
|---|---|
| `REDIS_HOST` | Redis hostname or IP |
| `REDIS_PORT` | Redis port (default 6379) |
| `REDIS_PASSWORD` | Redis auth password |

---

## Running samples

```bash
pip install jordan_py
# or from source:
pip install -e libraries/python/jordan_py

python sample/01-simple-message-status.py   # register → status loop → read message
python sample/02-custom-actions.py          # custom actions with typed parameters
python sample/03-async.py                   # async (non-blocking) message reading
python sample/04-multi-tasks.py             # multiple sub-tasks in parallel
```

---

## Naming conventions

### DTOs (REST payloads)

| Name | Description |
|---|---|
| `JordanActionsDefinition` | List of actions a passive client declares at registration |
| `JordanStatus` | Status update sent by passive client |
| `JordanMessage` | Message sent by active client to passive client |
| `JordanClientModel` | Client registration record |
| `JordanTaskModel` | Sub-task record |

### Status types

```python
FAILURE_STATUS_TYPE  = 'failure'
SUCCESS_STATUS_TYPE  = 'success'
GENERAL_STATUS_TYPE  = 'general'
PROGRESS_STATUS_TYPE = 'progress'
```

### Message state machine

```
SERVER_RECEIVED
  ↓  (server delivers to passive client)
MESSAGE_DELIVERED
  ↓  (passive client calls read_message())
CLIENT_RECEIVED
  ↓  (passive client calls msg.acknowledge())
MESSAGE_ACKNOWLEDGED
  ↓  (passive client calls msg.processed())
MESSAGE_PROCESSED          ← normal terminal state
```

Error/alternate terminal states: `ERROR_CANNOT_PROCESS_MESSAGE`, `MESSAGE_OVERRIDDEN`

### Task states

`STARTED → RUNNING → PAUSED → COMPLETE | ERROR | TIME_OUT`

### Action parameter types

```python
PARAMETER_TYPE_STRING = 'string'
PARAMETER_TYPE_INT    = 'int'
PARAMETER_TYPE_FLOAT  = 'float'
```

---

## Language

All code, comments, commit messages, and documentation in this project are written in **English**.
