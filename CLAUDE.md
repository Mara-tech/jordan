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
  admin_identity.py Operator accounts, roles, permissions
  rejson_interface.py  Redis read/write layer
  jordan_constants.py  Port, host, Redis keys
  jordan_server.py  Entry point: python jordan_server.py
  mock.py           Dev/test data fixtures
  requirements.txt  Pinned dependencies

libraries/
  prototype/contract.md   API specification (authoritative)
  python/jordan_py/       Python passive-client library (jordan_py on PyPI)
  cli/jordan_cli/         CLI wrapping jordan_py (jordan_cli on PyPI)
  java/jordan-core/       Shared DTOs, constants, and utilities (used by jordan-client and the Android app)
  java/jordan-client/     Passive-client Java library — Java counterpart of jordan_py

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
| `JORDAN_ADMIN_USERS` | Operator accounts guarding `/jordan/admin/*` (JSON array) |
| `JORDAN_ADMIN_TOKEN` | Shared bootstrap token for `/jordan/admin/*` |
| `JORDAN_ADMIN_SESSION_TTL` | Admin session lifetime in seconds (default 43200) |
| `JORDAN_REGISTRATION_KEY` | Key required to register a passive client, or a JSON object naming several (unset = registration open) |
| `JORDAN_REGISTRATION_RATE_LIMIT` | Max registration attempts per caller and per window (default 20, `0` disables) |
| `JORDAN_REGISTRATION_RATE_WINDOW` | Length of that window in seconds (default 60) |

---

## Authentication

Both namespaces are guarded by `Authorization: Bearer <token>`, with a different token each:

| Namespace | Guard (`server/api.py`) | Token |
|---|---|---|
| `/jordan/client/*` | `_require_client_auth(task_id)` | per-client `authToken` returned by `register`, validated against Redis by walking up to the root task |
| `/jordan/admin/*` | `_require_admin_auth(permission)` | session token from `POST /admin/login` (stored in Redis under its hash, with a TTL), or the shared `JORDAN_ADMIN_TOKEN` |

Open routes: `POST /jordan/client/register`, `POST /jordan/admin/login`, `GET /jordan/hello`,
`GET /jordan/admin/hello`.

### Registering a passive client

`POST /jordan/client/register` is open by design, which a public server can close by setting
`JORDAN_REGISTRATION_KEY`: the caller then sends that key as `Authorization: Bearer <key>`
(`401` otherwise). The key goes in the header, never in the payload — the payload is logged and
stored as the client record. `jordan_py`, `jordan_cli` and `jordan-client` (Java) all take it as an
optional `registration_key` / `registrationKey` argument and fall back to the environment variable
of the same name. It is an admission ticket only: what authorizes every later call is the
per-client `authToken` registration returned.

The variable holds one key, or a JSON object naming several
(`{"retiring":"<old>","current":"<new>"}`), so a key can be replaced without a flag day — publish
the new one beside the old, move the clients over, drop the retired entry. Each accepted
registration logs the *name* of the key used (a `key#<fingerprint>` for a lone unnamed key), never
the key, which is how you see the old one fall out of use.

A value that is set but unusable — malformed JSON, an empty object, an entry without a key or a
name, a JSON array — raises `ConfigurationError` (defined in
[server/jordan_constants.py](server/jordan_constants.py), shared with `admin_identity`). Both
declarations are validated by `check_configuration()`, called **at import of
[server/api.py](server/api.py)** and not from `start_api()`: `gunicorn api:app` never calls the
latter, and production is where the check matters. It is all-or-nothing on purpose — skipping one
bad entry would leave a key its operator believes valid, silently refusing the clients holding it.
`registration_keys()` returns `None` when the variable is unset (registration open); the guard also
keeps a request-time refusal (`401`), unreachable through a server that booted but making sure a
broken declaration can never read as "open".

Attempts are counted per caller address in Redis (`count_registration_attempt`), successful or not,
so key guessing is throttled as well: past `JORDAN_REGISTRATION_RATE_LIMIT` attempts in
`JORDAN_REGISTRATION_RATE_WINDOW` seconds the server answers `429`. Behind a proxy the address is
the *last* entry of `X-Forwarded-For` — the one the proxy appended; anything a caller forges sits to
its left.

Operator accounts live in `JORDAN_ADMIN_USERS` (JSON array of `{login, passwordHash, role}`,
hashes produced by `python server/admin_identity.py <login> <password> [role]`). An unusable
declaration — malformed JSON, an account without a `passwordHash`, an unknown role, a login twice —
raises `ConfigurationError` from `load_operators()` and stops the boot, same rule as the
registration keys: dropping the faulty account would lock its holder out silently. `[]` stays valid
(no named operator, the shared token alone) — it is what `docker-compose.yml` passes by default.
Roles map to permissions in `server/admin_identity.py`:

| Role | `read` | `send` | `delete` |
|---|---|---|---|
| `viewer` | ✔ | | |
| `operator` | ✔ | ✔ | |
| `admin` | ✔ | ✔ | ✔ |

`401` when the token is missing, unknown or expired; `403` when the role lacks the permission.
The `author` of a message is set from the authenticated identity, overriding the request body.

The admin namespace **fails closed** — with neither variable set, every admin request is
rejected with `401` rather than served openly, and the server logs an error at startup.

Active clients open a session and send the token themselves:

| Active client | Login | Token storage |
|---|---|---|
| `app/android` | `POST /admin/login` from `LoginDialog` — the only screen asking for credentials — or, when it was told to remember them, from the `JordanServer` row and `JordanSecretStore` | `JordanSession`, in memory, keyed by server base URL |
| `jordan-admin` (CLI) | `jordan-admin login` (password prompted, or `$JORDAN_ADMIN_PASSWORD`); `--token` / `$JORDAN_ADMIN_TOKEN` skips it for scripts and machine-to-machine callers | `~/.jordan_admin_session`, created `0600`, keyed by server base URL |

Both key the token by server URL and never send it elsewhere: a token is a credential of the
server that issued it. In `jordan_cli/admin.py`, `--token` wins over the stored session, an expired
or missing one stops the command before it calls, and `403` is reported as a role problem rather
than as a generic error.

Remembered credentials are split in two on the device: the `JordanServer` Room row keeps the
`login` and the row id, `JordanSecretStore` keeps the password encrypted under an AES key held by
the Android Keystore (`SharedPreferences` file `jordan_server_secrets`, entries keyed by row id).
No secret is left in the database, so exports (`ExportServerDialog`), backups and a copied
database file cannot carry one. Below API 23 there is no Keystore key: the app declines to
remember rather than storing in clear (`JordanSecretStore.isAvailable()`).

In the Android app, `NetworkUtils.makeHeaders(server)` adds the header to every Volley request,
and `JordanApi` turns a `401` into `JordanAuthenticationListener.onAuthenticationRequired()` —
which the visible `InServerFragment` answers with the login dialog, then reloads its screen.

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
| `jordan-core` + `jordan-client` | `java/v*` | *(planned)* | GitHub Packages |

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

