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
  cli/jordan_cli/         CLI wrapping jordan_py (jordan_cli on PyPI)
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

## Task hierarchy

Every registered client is a **root task**. Tasks can be nested arbitrarily: a root task can have sub-tasks, which can have their own sub-tasks. This is the central organizational unit of Jordan.

```
root task  (created by register)
├── sub-task A  (created by create_task / jordan task-create)
│   └── sub-sub-task A1
└── sub-task B
```

Key rules:
- Status updates, messages, and state changes are always addressed to a specific `task_id`.
- Only the root task is unregistered at the end of a session; sub-tasks are simply marked COMPLETE or ERROR.
- In the Python library, `JordanInstance.create_task()` returns a `JordanTaskInstance` (same API as `JordanInstance`, but `fatal()` does not unregister).
- In the CLI, `jordan task-create NAME` creates a sub-task and prints its ID. All commands accept `--task-id` to target a sub-task; omitting it targets the root task.

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

## Release process

Each component has its own prefixed tag. Only the matching workflow fires.

| Component | Tag pattern | Workflow | Target |
|---|---|---|---|
| `jordan_py` | `jordan_py/v*` | `release-library-python.yml` | PyPI |
| `jordan_cli` | `jordan_cli/v*` | `release-cli-python.yml` | PyPI |
| `server` | `server/v*` | `release-server.yml` | ghcr.io Docker image |
| `app/android` | `android/v*` | `release-android.yml` | APK artifact |

**To release `jordan_py`:**
1. Bump `version` in [`libraries/python/jordan_py/pyproject.toml`](libraries/python/jordan_py/pyproject.toml)
2. Commit and push
3. Tag and push:
   ```bash
   git tag jordan_py/v1.1.0 && git push origin jordan_py/v1.1.0
   ```

**To release `jordan_cli`:**
1. Bump `version` in [`libraries/cli/pyproject.toml`](libraries/cli/pyproject.toml)
2. Commit and push
3. Tag and push:
   ```bash
   git tag jordan_cli/v1.0.0 && git push origin jordan_cli/v1.0.0
   ```

The same pattern applies to `server` and `android` with their respective prefixes.

---

## Language

All code, comments, commit messages, and documentation in this project are written in **English**.

---

### Rules

- Keep documentation (README.md, CLAUDE.md and other Markdown), deployment and other ci/cd files up to date

