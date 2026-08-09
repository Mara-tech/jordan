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
Swagger UI: `http://localhost:5000/jordan/swagger-ui`, once `JORDAN_ENABLE_DOCS=true` is in your
`.env` — a server that declares nothing serves the API alone, and publishes neither its docs nor
the debugger. In production, run it under gunicorn: `gunicorn api:app`. See
[server/README.md](server/README.md#what-the-server-exposes-of-itself).

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
| `JORDAN_ADMIN_USERS` | Operator accounts guarding `/jordan/admin/*` (JSON array) | — |
| `JORDAN_ADMIN_TOKEN` | Shared bootstrap token for `/jordan/admin/*` | — |
| `JORDAN_ADMIN_SESSION_TTL` | Lifetime of an admin session token, in seconds | `43200` (12 h) |
| `JORDAN_REGISTRATION_KEY` | Key a passive client must present to register, or a JSON object naming several | — (registration open) |
| `JORDAN_REGISTRATION_RATE_LIMIT` | Registration attempts allowed per caller and per window (`0` disables) | `20` |
| `JORDAN_REGISTRATION_RATE_WINDOW` | Length of that window, in seconds | `60` |
| `JORDAN_DEBUG` | Werkzeug debugger on the development server | `false` |
| `JORDAN_ENABLE_DOCS` | Publish Swagger UI and the OpenAPI spec behind it | follows `JORDAN_DEBUG` |

With neither `JORDAN_ADMIN_USERS` nor `JORDAN_ADMIN_TOKEN` set, every admin request returns `401`.

The last two are off unless declared: the debugger is a Python console for whoever reaches the
port, and the spec is the complete map of the API. A deployment that says nothing serves the API
alone.

---

## Authentication

Both namespaces expect an `Authorization: Bearer <token>` header, with a different token each:

| Namespace | Token | Issued by |
|---|---|---|
| `/jordan/client/*` | per-client `authToken` | the server, in the `POST /jordan/client/register` response |
| `/jordan/admin/*` | operator session token | the server, in the `POST /jordan/admin/login` response |

Admin operators hold one of three roles — `viewer` (read), `operator` (read + send messages),
`admin` (read + send + delete) — and a call whose role lacks the permission gets `403`. The
`author` of a message is the authenticated operator, not a value from the request body.

Client registration, admin login and the `hello` health endpoints are the only open routes.
See [`server/README.md`](server/README.md#authentication) for account creation and the login flow.

Registration is open by design, and a public server can close it with `JORDAN_REGISTRATION_KEY` —
passive clients then send that key as their bearer token when registering:

```python
j = jordan.register('https://your-server/jordan/', registration_key='<key>')
# or leave it out and export JORDAN_REGISTRATION_KEY instead
```

The key only opens registration — every later call is authorized by the `authToken` the client was
issued. Set the variable to a JSON object (`{"retiring":"<old>","current":"<new>"}`) to accept
several named keys at once: that is how one is replaced without updating every client at the same
minute, and how each population (CI, laptops, a partner) gets a key that can be revoked on its own.
A value that is set but unusable stops the server at startup instead of closing registration
silently. See [`server/README.md`](server/README.md#several-keys-and-rotating-one).

Registrations are rate-limited per caller address whether they succeed or not (`429` past the
limit), which also throttles guessing of the key.

Active clients open a session themselves and send the token on every call:

| Active client | Logs in with | Token storage |
|---|---|---|
| [`app/android`](app/android/README.md#authentication) | the credentials saved for the selected server, or the login dialog the first time a call is refused | in memory, keyed by server URL |
| [`jordan-admin`](libraries/cli/README.md#admin-cli-jordan-admin) | `jordan-admin login`, or `--token` / `$JORDAN_ADMIN_TOKEN` for scripts | `~/.jordan_admin_session`, owner-readable only, keyed by server URL |

---

## Libraries

| Library | Language | Description |
|---|---|---|
| [`jordan_py`](libraries/python/jordan_py/README.md) | Python | Passive-client library — register, send status, read messages |
| [`jordan_cli`](libraries/cli/README.md) | Python (CLI) | Shell-friendly CLI wrapping `jordan_py`; includes `jordan-admin` operator commands |
| [`jordan-client`](libraries/java/jordan-client/README.md) | Java 11+ | Passive-client library — Java counterpart of `jordan_py` |
| [`jordan-core`](libraries/java/jordan-core/README.md) | Java 8+ | Shared DTOs, constants, and utilities used by `jordan-client` and the Android app |

> **Note:** `jordan-core` is consumed by the Android app as a local Gradle project (see `app/android/settings.gradle`). The two Java libraries are built together from `libraries/java/` using a single multi-module Gradle build.
