# Jordan Server

Flask-RESTX server that acts as the central hub between passive clients (executing programs) and active clients (admin UIs, bots).

## Requirements

- Python 3.8+
- A Redis instance (local, Redis Cloud, Upstash, etc.) with JSON module enabled

## Setup

```bash
cd server
cp .env.example .env
```

Edit `.env` and fill in your Redis credentials, then declare at least one admin credential
(see [Authentication](#authentication)):

```
REDIS_HOST=your-redis-host.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
JORDAN_ADMIN_USERS=[{"login": "alice", "passwordHash": "pbkdf2:sha256:...", "role": "admin"}]
```

Create that account entry with:

```bash
python admin_identity.py alice <password> admin
```

Install dependencies:

```bash
pip install -r requirements.txt
```

## Running

```bash
# from the repo root
python jordan_server.py
```

The server starts on port **5000** and prints its URL on startup.

| Endpoint | Description |
|---|---|
| `http://<host>:5000/jordan/` | REST API root |
| `http://<host>:5000/jordan/swagger-ui` | Interactive API docs (Swagger UI) |
| `http://<host>:5000/jordan/client/` | Passive client endpoints |
| `http://<host>:5000/jordan/admin/` | Active client / admin endpoints |

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `REDIS_HOST` | Yes | Redis hostname or IP |
| `REDIS_PORT` | Yes | Redis port (typically `6379`) |
| `REDIS_PASSWORD` | Yes | Redis authentication password |
| `JORDAN_ADMIN_USERS` | Yes\* | JSON array of operator accounts guarding `/jordan/admin/*` |
| `JORDAN_ADMIN_TOKEN` | Yes\* | Shared bootstrap token: full permissions, no named operator |
| `JORDAN_ADMIN_SESSION_TTL` | No | Lifetime in seconds of a session token (default `43200`, 12 h) |

\* at least one of the two — with neither, every admin request is rejected.

## Authentication

Both namespaces expect `Authorization: Bearer <token>`, with a different token each:

| Namespace | Token | Issued by |
|---|---|---|
| `/jordan/client/*` | per-client `authToken` | the server, in the `POST /jordan/client/register` response |
| `/jordan/admin/*` | operator session token | the server, in the `POST /jordan/admin/login` response |
| `/jordan/admin/*` | shared bootstrap token | you, through `JORDAN_ADMIN_TOKEN` |

`POST /jordan/client/register`, `POST /jordan/admin/login`, `GET /jordan/hello` and
`GET /jordan/admin/hello` are the only open endpoints.

The admin namespace **fails closed**: with neither `JORDAN_ADMIN_USERS` nor `JORDAN_ADMIN_TOKEN`
set, every `/jordan/admin/*` request returns `401` instead of serving data openly. The server
logs an error at startup when this happens.

### Operator accounts and roles

Accounts live in `JORDAN_ADMIN_USERS`, a JSON array of `{login, passwordHash, role}`. Passwords
are stored hashed (pbkdf2-sha256, via Werkzeug) — never in clear text. Add an account with:

```bash
python admin_identity.py bob <password> operator
# prints: {"login": "bob", "passwordHash": "pbkdf2:sha256:...", "role": "operator"}
```

| Role | `read` | `send` | `delete` |
|---|---|---|---|
| `viewer` | ✔ | | |
| `operator` | ✔ | ✔ | |
| `admin` | ✔ | ✔ | ✔ |

- **read** — list clients and actions, read statuses and messages, generic query
- **send** — send a message (command) to a passive client
- **delete** — delete a task, a client, or the whole base

A token whose role lacks the permission gets `403`; a missing or expired token gets `401`.

The whole json string is to put in `.env` file.
You can test it locally for example from Swagger UI :
1. Call `/admin/login` with your login/password as json in the payload body
2. Copy the token returned in the successful response
3. Click the Top right-hand Authorize button
4. In `Value` field, write : `Bearer <paste-your-token>`. Do **not only paste** the token.
5. Try another `admin/` endpoint, one with data, or simply `admin/me` to check your identity and role.


### Logging in

```bash
TOKEN=$(curl -s -X POST http://localhost:5000/jordan/admin/login \
  -H 'Content-Type: application/json' \
  -d '{"login": "bob", "password": "<password>"}' | python -c "import json,sys; print(json.load(sys.stdin)['token'])")

curl -H "Authorization: Bearer $TOKEN" http://localhost:5000/jordan/admin/clients
curl -H "Authorization: Bearer $TOKEN" http://localhost:5000/jordan/admin/me      # role and permissions
curl -X POST -H "Authorization: Bearer $TOKEN" http://localhost:5000/jordan/admin/logout
```

Session tokens are stored in Redis under the *hash* of the token, with a TTL
(`JORDAN_ADMIN_SESSION_TTL`): a leaked token stops working on its own, and a dump of the
database never yields a usable token.

The `author` of a message is taken from the token of whoever sends it — an `author` field in the
request body is ignored.

In Swagger UI, use the **Authorize** button to set the header for a whole session.

### Shared bootstrap token

`JORDAN_ADMIN_TOKEN` still works, carries every permission, and reports the conventional login
`shared-admin`. It exists for first setup and machine-to-machine callers; prefer named operators
everywhere else, since only they give meaningful message authorship and least privilege.

## File overview

| File | Role |
|---|---|
| `jordan_server.py` | Entry point |
| `api.py` | REST endpoints — `client_ns` and `admin_ns` namespaces |
| `admin_identity.py` | Operator accounts, roles and permissions; also the account-creation helper |
| `rejson_interface.py` | Redis read/write layer |
| `jordan_constants.py` | Port, host, API path prefix |
| `jordan_log.py` | Logging helpers |
| `mock.py` | Dev/test data fixtures |
| `requirements.txt` | Pinned Python dependencies |
| `.env.example` | Template for environment variables |
