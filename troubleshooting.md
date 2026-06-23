# Troubleshooting

## Server

### Cannot start Flask API — `cannot import name 'cached_property' from 'werkzeug'`

**Cause:** This error occurs with `flask-restx < 1.0` combined with `Werkzeug >= 1.0`. The old workaround was to downgrade Werkzeug to 0.16.1 — **do not do this.**

**Fix:** Install the pinned dependencies from `requirements.txt`, which uses `flask-restx>=1.0` compatible with modern Werkzeug:

```bash
pip install -r server/requirements.txt
```

---

## Redis

### Connection refused / timeout on startup

**Symptoms:** Server starts but crashes immediately, or all API calls return 500.

**Check:** Verify the `server/.env` file exists and contains correct values:

```
REDIS_HOST=<your-redis-host>
REDIS_PORT=6379
REDIS_PASSWORD=<your-redis-password>
```

If using Redis Cloud, the host looks like `redis-xxxxx.c1.us-east-1-1.ec2.redns.redis-cloud.com` and the port is typically not 6379 — copy both from your Redis Cloud dashboard.

### Authentication error — `WRONGPASS invalid username-password pair`

**Fix:** Double-check `REDIS_PASSWORD` in `server/.env`. The password is the **Default user password** shown in Redis Cloud (not the account password).

### TLS / SSL connection errors

Some Redis providers (Redis Cloud on paid plans, Upstash) require TLS. If you see SSL-related errors:

1. Check your provider's dashboard for whether TLS is mandatory.
2. If TLS is required, the `redis[hiredis]` client connects via `rediss://` (note the double `s`) — update the connection parameters in `server/rejson_interface.py` to pass `ssl=True`.
