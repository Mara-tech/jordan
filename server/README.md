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
# development, from the server/ directory
python jordan_server.py
```

The server starts on port **5000** (or `$PORT`) and reports on startup what it accepts: how many
operator accounts it found, whether registration is open, and whether it publishes its own
documentation.

| Endpoint | Description |
|---|---|
| `http://<host>:5000/jordan/` | REST API root |
| `http://<host>:5000/jordan/swagger-ui` | Interactive API docs — only when [enabled](#what-the-server-exposes-of-itself) |
| `http://<host>:5000/jordan/client/` | Passive client endpoints |
| `http://<host>:5000/jordan/admin/` | Active client / admin endpoints |

### Checking the settings without starting a server

```bash
JORDAN_ADMIN_USERS='[{"login": "alice", "passwordHash": "...", "role": "operator"}]' \
  python jordan_server.py --check
```

Exits `0` and says so when the environment would let a server start, non-zero with the reason when
it would not. It runs the same validation the boot does — importing the app is what performs it —
and serves nothing.

Worth running before setting a variable on a platform, because of how the failure otherwise
presents itself: a declaration that cannot be honoured stops the boot on purpose, the platform
keeps the previous deployment serving, and *that* one answers the URL and writes the logs. The
explicit refusal exists, in the dead deployment's log stream, while the live one reports the
configuration it started with — often the opposite of what you just set.

### In production

`jordan_server.py` starts Flask's development server, which is single-process and not written to
face a network. Anywhere else than a laptop, run the same app under `gunicorn` (installed by
`requirements.txt`):

```bash
# from the server/ directory
gunicorn api:app --bind 0.0.0.0:${PORT:-5000} --workers 2
```

`api:app` is the WSGI application, so `start_api()` is never called — which is why the settings
are checked and reported when the module is *imported*, and why `JORDAN_DEBUG` below has no
meaning under gunicorn: there is no development server to put a debugger in.

[`Dockerfile`](Dockerfile) runs that same command, as an unprivileged `jordan` user that owns
none of the files it serves. It carries no secret: everything in the table below is passed as an
environment variable at run time, and [`.dockerignore`](.dockerignore) keeps `.env` out of the
image — being gitignored only stops it reaching the repository, not a `COPY . .`. See
[RAILWAY_DEPLOYMENT.md](RAILWAY_DEPLOYMENT.md) for a deployment end to end.

## Environment variables

| Variable | Required | Description |
|---|---|---|
| `REDIS_HOST` | Yes | Redis hostname or IP |
| `REDIS_PORT` | Yes | Redis port (typically `6379`) |
| `REDIS_PASSWORD` | Yes | Redis authentication password |
| `REDIS_SSL` | No | Encrypt the connection to Redis (default `false`, see [Reaching Redis](#reaching-redis)) |
| `JORDAN_ADMIN_USERS` | Yes\* | JSON array of operator accounts guarding `/jordan/admin/*` |
| `JORDAN_ADMIN_TOKEN` | Yes\* | Shared bootstrap token: full permissions, no named operator |
| `JORDAN_ADMIN_SESSION_TTL` | No | Lifetime in seconds of a session token (default `43200`, 12 h) |
| `JORDAN_REGISTRATION_KEY` | No | Key a passive client must present to register, or a JSON object naming several — unset leaves registration open |
| `JORDAN_REGISTRATION_RATE_LIMIT` | No | Registration attempts allowed per caller and per window (default `20`, `0` disables) |
| `JORDAN_REGISTRATION_RATE_WINDOW` | No | Length of that window in seconds (default `60`) |
| `JORDAN_DEBUG` | No | Werkzeug debugger on the development server (default `false`) |
| `JORDAN_ENABLE_DOCS` | No | Publish Swagger UI and the OpenAPI spec (defaults to `JORDAN_DEBUG`) |

\* at least one of the two — with neither, every admin request is rejected.

The booleans read `1`/`true`/`yes`/`on` and their opposites, in any case. What an unreadable value
costs depends on which way the default leans: `JORDAN_DEBUG` and `JORDAN_ENABLE_DOCS` fall back to
their default with a line in the log, since that withholds something, while `REDIS_SSL` stops the
server — falling back there would downgrade the connection to clear text without a word.

## Reaching Redis

`REDIS_SSL` is off by default, because that is the only value the `docker-compose.yml` stack can
use: it starts a Redis on a private docker network which serves no certificate. The same goes for
one on loopback.

A managed instance — Redis Cloud, Upstash — is a different situation: it is reached over the
internet, and every exchange with it opens with `REDIS_PASSWORD`, followed by the payloads this
server stores. That is true of a laptop pointed at one as much as of a deployment, so the default
being off is a convenience of the local stack, not a judgement that the link is safe. Turn it on
there:

```
REDIS_SSL=true
```

The server certificate is verified against the system CA store. There is deliberately no setting to
skip that check: a TLS connection that accepts any certificate proves nothing about who is on the
other end.

TLS is a property of the database as much as of the client: enable it in the provider's console
first, on the same endpoint, then set the variable. Done in the other order, the handshake fails
and the server says so in its logs — it never falls back to clear text, which is the point.

Two things to check with your provider before assuming the variable is enough:

- **the plan may not offer TLS at all.** On Redis Cloud it is available on paid Essentials plans and
  on Pro; [the free 30 MB Essentials plan has no TLS](https://redis.io/docs/latest/operate/rc/security/database-security/tls-ssl/).
  There, `REDIS_SSL=true` does not encrypt anything — it stops the connection from working.
- **the certificate may not chain to a public CA.** Redis Cloud's `redis_ca.pem` bundle carries a
  publicly trusted GlobalSign root *and* two self-signed Redis Cloud roots still in use. A database
  presenting the latter fails verification against the system store, and the CA bundle has to be
  handed to the client — which this server does not yet support.

The server reports which of the two it opened at startup, beside what it accepts:

```
[INFO] Redis connection is encrypted
[INFO] REDIS_SSL is off: the Redis password and everything this server stores travel in clear, ...
```

## What the server exposes of itself

Beyond the API, a Flask server can publish two things that belong to development only. Both are
**off unless declared**, so a deployment that says nothing serves the API and nothing else.

| Variable | Off (default) | On |
|---|---|---|
| `JORDAN_DEBUG` | errors are logged, callers get a plain `500` | Werkzeug debugger: an interactive Python console on the error page |
| `JORDAN_ENABLE_DOCS` | `/jordan/swagger-ui` and `/jordan/swagger.json` do not exist (`404`) | both are served |

The debugger executes what a visitor types, in the server process, with its Redis credentials in
reach — it is a remote shell, not a verbose error page. The docs are milder: the spec holds no
secret, but it is the complete map of the API, and publishing it saves a stranger the work of
finding out what this server is and what it accepts.

`JORDAN_ENABLE_DOCS` defaults to `JORDAN_DEBUG`, so a laptop turns both on at once:

```bash
JORDAN_DEBUG=true python jordan_server.py     # debugger + Swagger UI
JORDAN_ENABLE_DOCS=true gunicorn api:app      # docs on a private deployment, no debugger
JORDAN_DEBUG=true JORDAN_ENABLE_DOCS=false python jordan_server.py   # the other way round
```

Hiding Swagger UI alone would hide nothing — the spec it reads is a URL of its own — so the switch
withholds `/jordan/swagger.json` as well.

## Authentication

Both namespaces expect `Authorization: Bearer <token>`, with a different token each:

| Namespace | Token | Issued by |
|---|---|---|
| `/jordan/client/*` | per-client `authToken` | the server, in the `POST /jordan/client/register` response |
| `/jordan/admin/*` | operator session token | the server, in the `POST /jordan/admin/login` response |
| `/jordan/admin/*` | shared bootstrap token | you, through `JORDAN_ADMIN_TOKEN` |

`POST /jordan/client/register`, `POST /jordan/admin/login`, `GET /jordan/hello` and
`GET /jordan/admin/hello` are the only open endpoints — and registration can be closed too, see
[Controlling registration](#controlling-registration).

The admin namespace **fails closed**: with neither `JORDAN_ADMIN_USERS` nor `JORDAN_ADMIN_TOKEN`
set, every `/jordan/admin/*` request returns `401` instead of serving data openly. The server
logs an error at startup when this happens.

### Operator accounts and roles

Accounts live in `JORDAN_ADMIN_USERS`, a JSON array of `{login, passwordHash, role}`. Passwords
are stored hashed (pbkdf2-sha256, via Werkzeug) — never in clear text. Add an account with:

```bash
python admin_identity.py bob <password> operator
# prints: [{"login": "bob", "passwordHash": "pbkdf2:sha256:...", "role": "operator"}]
```

It prints the complete bracketed value first, then the bare entry to drop into an array that
already has accounts in it. A lone account is still an array: pasted without its brackets, the
declaration is refused and the server does not start.

| Role | `read` | `send` | `delete` |
|---|---|---|---|
| `viewer` | ✔ | | |
| `operator` | ✔ | ✔ | |
| `admin` | ✔ | ✔ | ✔ |

- **read** — list clients and actions, read statuses and messages, generic query
- **send** — send a message (command) to a passive client
- **delete** — delete a task, a client, or the whole base

A token whose role lacks the permission gets `403`; a missing or expired token gets `401`.
An account without a `role` is a `viewer`, the least of them.

A declaration that cannot be honoured — malformed JSON, an account without a `passwordHash`, an
unknown role, a login declared twice — makes the server **refuse to start**, for the same reason as
[a misconfigured registration key](#a-misconfigured-key-stops-the-server): dropping the faulty
account would lock its holder out, and nobody discovers that before the day they try to log in.
An empty array is not a mistake — it declares no named operator, and the shared token then stands
alone.

The whole json string is to put in `.env` file.
You can test it locally for example from Swagger UI — served once `JORDAN_ENABLE_DOCS` or
`JORDAN_DEBUG` is on, see [What the server exposes of itself](#what-the-server-exposes-of-itself) :
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

Or from the operator CLI, which does the same three calls and keeps the token for you:

```bash
jordan-admin login --server http://localhost:5000/jordan/ --login bob
jordan-admin list
jordan-admin whoami
jordan-admin logout
```

`jordan-admin` stores the token per server URL in `~/.jordan_admin_session`, owner-readable only,
and never sends it to another server than the one that issued it. `--token` / `$JORDAN_ADMIN_TOKEN`
bypasses the session file for scripts — that is where the shared bootstrap token below fits. See
[`libraries/cli/README.md`](../libraries/cli/README.md#admin-cli-jordan-admin).

Session tokens are stored in Redis under the *hash* of the token, with a TTL
(`JORDAN_ADMIN_SESSION_TTL`): a leaked token stops working on its own, and a dump of the
database never yields a usable token.

The `author` of a message is taken from the token of whoever sends it — an `author` field in the
request body is ignored.

In Swagger UI, use the **Authorize** button to set the header for a whole session.

### Shared bootstrap token

`JORDAN_ADMIN_TOKEN` still works, carries every permission, and reports the conventional login
`shared-admin`. It exists for first setup and machine-to-machine callers — `jordan-admin` reads it
from the environment variable of the same name; prefer named operators everywhere else, since only
they give meaningful message authorship and least privilege.

## Controlling registration

`POST /jordan/client/register` is open by design: any program that reaches the server can become a
passive client. On a public deployment, set `JORDAN_REGISTRATION_KEY` to close it:

```bash
python -c "import secrets; print(secrets.token_hex(32))"   # generate one
```

Passive clients then present that key when registering — as a bearer token, never in the payload,
which the server logs and stores as the client record:

```bash
curl -X POST http://localhost:5000/jordan/client/register \
  -H "Authorization: Bearer $JORDAN_REGISTRATION_KEY" \
  -H 'Content-Type: application/json' -d '{"name": "my-script"}'
```

```python
jordan.register('http://localhost:5000/jordan/', registration_key='<key>')
```

`jordan_py`, `jordan_cli` (`jordan register --registration-key`) and the Java `jordan-client` all
accept the key and fall back to the `JORDAN_REGISTRATION_KEY` environment variable when it is not
passed explicitly. A missing or wrong key gets `401`.

The key is an admission ticket, not a session credential: it gates *who may create clients*, and
nothing else. What a client sends on every later call is the `authToken` registration returned to
it, which is its own — the key never gives access to another client's statuses or messages.

### Several keys, and rotating one

A single key is shared by every passive client, so replacing it would mean updating all of them at
the same minute. Name several in a JSON object instead, and the server accepts each of them:

```
JORDAN_REGISTRATION_KEY={"retiring":"<old>","current":"<new>"}
```

Rotating then has no flag day:

1. add the new key beside the old one, and restart or redeploy;
2. move the clients over, one at a time;
3. drop the retired entry once the logs stop naming it.

That third step is what the names are for: every accepted registration logs which key was used
(`Registration accepted with 'retiring'`) and never the key itself. Without that, the end of a
rotation is a guess. The same mechanism gives one key per population (CI, laptops, a partner), so
one can be revoked without disturbing the others.

A single key still reads as a plain value, so nothing has to move to JSON:

```
JORDAN_REGISTRATION_KEY=<key>
```

### A misconfigured key stops the server

A value that is set but cannot be honoured — malformed JSON, an empty object, an entry without a
key or without a name, a JSON array — makes the server **refuse to start**, naming what is wrong:

```
jordan_constants.ConfigurationError: JORDAN_REGISTRATION_KEY: 'ci' carries no key
```

It is deliberately all-or-nothing. Skipping the one bad entry would leave a key its operator
believes valid, silently refusing every client that holds it — the failure this check exists to
prevent. `JORDAN_ADMIN_USERS` is checked the same way, and for the same reason.

The check runs when `api.py` is imported, so `python jordan_server.py` and `gunicorn api:app` both
hit it — `start_api()` would have been skipped by the second. On a platform with a health check
(Railway) the bad deployment then never takes traffic, and the previous one keeps serving.

### Rate limit

Registration attempts are counted per caller address in Redis, successful or not — guessing the key
is throttled by the same counter. Past `JORDAN_REGISTRATION_RATE_LIMIT` attempts within
`JORDAN_REGISTRATION_RATE_WINDOW` seconds (20 per minute by default), the server answers `429` until
the window closes. Set the limit to `0` to disable the check.

Behind a reverse proxy the peer address is the proxy's, so the caller is read from the **last**
entry of `X-Forwarded-For`, the one the proxy appended; entries a caller forges sit to its left and
do not earn it a fresh bucket. Deployed without a proxy that sets the header, everything a NAT hides
shares one bucket — raise the limit accordingly.

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
