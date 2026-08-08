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

Edit `.env` and fill in your Redis credentials and the admin token:

```
REDIS_HOST=your-redis-host.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
JORDAN_ADMIN_TOKEN=<generated token>
```

Generate the admin token with:

```bash
python -c "import secrets; print(secrets.token_hex(32))"
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
| `JORDAN_ADMIN_TOKEN` | Yes | Shared token protecting `/jordan/admin/*`. Unset ⇒ every admin request is rejected |

## Authentication

Both namespaces expect `Authorization: Bearer <token>`, with a different token each:

| Namespace | Token | Issued by |
|---|---|---|
| `/jordan/client/*` | per-client `authToken` | the server, in the `POST /jordan/client/register` response |
| `/jordan/admin/*` | shared admin token | you, through `JORDAN_ADMIN_TOKEN` |

`POST /jordan/client/register`, `GET /jordan/hello` and `GET /jordan/admin/hello` are the only open endpoints.

The admin namespace **fails closed**: if `JORDAN_ADMIN_TOKEN` is empty or missing, every
`/jordan/admin/*` request returns `401` instead of serving data openly. The server logs an
error at startup when this happens.

```bash
curl -H "Authorization: Bearer $JORDAN_ADMIN_TOKEN" http://localhost:5000/jordan/admin/clients
```

In Swagger UI, use the **Authorize** button to set the header for a whole session.

## File overview

| File | Role |
|---|---|
| `jordan_server.py` | Entry point |
| `api.py` | REST endpoints — `client_ns` and `admin_ns` namespaces |
| `rejson_interface.py` | Redis read/write layer |
| `jordan_constants.py` | Port, host, API path prefix |
| `jordan_log.py` | Logging helpers |
| `mock.py` | Dev/test data fixtures |
| `requirements.txt` | Pinned Python dependencies |
| `.env.example` | Template for environment variables |
