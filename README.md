# Jordan

Jordan lets an executing program be interacted with from anywhere — send it commands, read its status, trigger actions.

## Architecture

```
┌─────────────────────────────────────────────────────────┐
│  Active client  (admin UI, bot, human operator)         │
│  → sends messages · reads status · triggers actions     │
└──────────────────────┬──────────────────────────────────┘
                       │ REST /jordan/admin/*
┌──────────────────────▼──────────────────────────────────┐
│  Central server  (server/)                              │
│  Flask-RESTX · Redis backend · port 5000                │
└──────────────────────┬──────────────────────────────────┘
                       │ REST /jordan/client/*
┌──────────────────────▼──────────────────────────────────┐
│  Passive client  (your executing program)               │
│  registers · sends status updates · reads messages      │
└─────────────────────────────────────────────────────────┘
```

**Protocol specification:** [`libraries/prototype/contract.md`](libraries/prototype/contract.md)

---

## Quick Start

### 1. Start the server

```bash
cd server
cp .env.example .env    # fill in your Redis credentials
pip install -r requirements.txt
python jordan_server.py
```

Server is available at `http://localhost:5000/jordan`.  
Swagger UI: `http://localhost:5000/jordan/swagger-ui`

### 2. Install the Python library

```bash
pip install jordan_py
# or from source:
pip install -e libraries/python/jordan_py
```

### 3. Register your program and interact

```python
from jordan_py import jordan

# Register to the Jordan server
j = jordan.register('http://localhost:5000/jordan/')

# Send a status update
j.send_status('Program started.')

# Read an incoming message (non-blocking)
msg = j.read_message()
if msg:
    print(f"Received action: {msg.action_name}")
    msg.acknowledge()
    msg.processed()

# Unregister when done
j.unregister()
```

### 4. Run the samples

```bash
python sample/01-simple-message-status.py   # register → status loop → read message
python sample/02-custom-actions.py          # custom actions with typed parameters
python sample/03-async.py                   # async (non-blocking) message reading
python sample/04-multi-tasks.py             # multiple sub-tasks in parallel
```

---

## Environment variables

Set these in `server/.env` before starting the server:

| Variable | Description | Default |
|---|---|---|
| `REDIS_HOST` | Redis hostname or IP | — |
| `REDIS_PORT` | Redis port | `6379` |
| `REDIS_PASSWORD` | Redis auth password | — |

---

## Libraries

| Library | Language | Description |
|---|---|---|
| [`jordan_py`](libraries/python/jordan_py/README.md) | Python | Passive-client library — register, send status, read messages |
| [`jordan_cli`](libraries/cli/README.md) | Python (CLI) | Shell-friendly CLI wrapping `jordan_py`; includes `jordan-admin` operator commands |
| [`jordan-client`](libraries/java/jordan-client/README.md) | Java 11+ | Passive-client library — Java counterpart of `jordan_py` |
| [`jordan-core`](libraries/java/jordan-core/README.md) | Java 8+ | Shared DTOs, constants, and utilities used by `jordan-client` and the Android app |

> **Note:** `jordan-core` is consumed by the Android app as a local Gradle project (see `app/android/settings.gradle`). The two Java libraries are built together from `libraries/java/` using a single multi-module Gradle build.
