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

Edit `.env` and fill in your Redis credentials:

```
REDIS_HOST=your-redis-host.example.com
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password
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
