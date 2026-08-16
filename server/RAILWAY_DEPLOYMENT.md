# Deploying the Jordan Server on Railway

## Prerequisites

1. A Railway account (https://railway.app)
2. The Railway CLI: `npm install -g @railway/cli`
3. Git configured for this repository

## Deployment architecture

```
                 HTTPS (Railway proxy)
                        │
┌───────────────────────▼─────────┐
│  Railway (App Container)        │
│  gunicorn api:app  ·  $PORT     │
│  non-root user "jordan"         │
└──────────────┬──────────────────┘
               │ TLS · REDIS_HOST, PORT, PASSWORD
┌──────────────▼──────────────────┐
│  RedisCloud (External Service)  │
│  Managed Redis (free up to 30MB)│
└─────────────────────────────────┘
```

Both links leave the machine. The Railway proxy encrypts the upper one; the lower one is encrypted
only if `REDIS_SSL=true` — see step 3.

## Step 1: Create a Railway project

```bash
railway init
# Choose: Create new project
# Project name: jordan
```

railway project link jordan

## Step 2: Set up RedisCloud

1. Go to https://app.rediscloud.com
2. Create a free database (30MB)
3. Copy the connection details:
   - Endpoint: `host:port`
   - Password: your password

The free 30MB plan carries one limitation that matters for a public deployment: it offers no TLS,
so the link between Railway and the database cannot be encrypted. See step 3.

## Step 3: Configure the environment variables on Railway
Catch: the service has to exist before variables can be added to it. But `railway up` starts the
service without them, so it crashes.

```bash
railway variable set REDIS_HOST=your-redis-host.rediscloud.com
railway variable set REDIS_PORT=12345
railway variable set REDIS_PASSWORD=your-redis-password
# set one of the following 
railway variable set JORDAN_ADMIN_TOKEN=$(python -c "import secrets; print(secrets.token_hex(32))")
railway variable set JORDAN_ADMIN_USERS=$(python admin_identity.py <login> <password> operator)

# only on a plan that offers TLS — see below before running this one
railway variable set REDIS_SSL=true
```

⚠️ `REDIS_SSL=true` is not a detail here: the Railway container and RedisCloud are two different
hosts, the traffic between them crosses the public internet, and every exchange opens with
`REDIS_PASSWORD` and then carries everything this server stores.

**Check the plan before setting it.**
[TLS is not available on the free 30 MB Essentials plan](https://redis.io/docs/latest/operate/rc/security/database-security/tls-ssl/) —
only on paid Essentials plans and on Redis Cloud Pro. On the free plan there is no toggle to turn
on, and the variable encrypts nothing: it just stops the connection from working, and the
deployment fails its health check.

That leaves three options on the free plan, and picking one is the point — the wrong move is to
leave the question open:

| Option | What it costs |
|---|---|
| Stay unencrypted, knowingly | free, but the Redis password and every status and message this server stores cross the internet readable to anything on the path. Defensible for a test deployment, not for one holding work you care about |
| Paid Essentials plan | the cheapest tier that has the TLS toggle |
| Another provider | [Upstash has TLS on by default on every database, free tier included](https://upstash.com/docs/redis/overall/compatibility), and implements a RedisJSON-compatible API — which this server needs. Its JSON support is a reimplementation rather than the Redis module, so it deserves a run of the test suite against it before the switch |

If you stay unencrypted, treat the deployment as one whose contents can be read: admin
authentication still guards `/jordan/admin/*` over HTTPS, so the API is not open — but the storage
link behind it is. And rotate `REDIS_PASSWORD` if the deployment ever becomes a real one, since by
then that password has been on the wire in clear for its whole life.

On a plan that does support TLS, enable it on the database first (RedisCloud console, same
endpoint), then set the variable. The other way round the handshake fails and the logs say so,
since the server never falls back to clear text. Enabling it applies to new connections only:
existing ones keep running until the client reconnects.

⚠️ the certificate is verified against the system authorities, and there is deliberately no setting
to skip that check. Redis Cloud's `redis_ca.pem` bundle contains a publicly trusted GlobalSign root
*and* two self-signed Redis Cloud roots that are deprecated but still in use — a database presenting
one of those fails verification, and passing the bundle to the client is not supported by this
server yet. Verify the handshake against a real database before relying on it.

**No secret goes into a file.** Not the `Dockerfile`, not [railway.json](railway.json), not a
`.env`: all three are versioned, and a Railway variable can be changed without rebuilding the
image. `server/.env` is covered twice — [.railwayignore](.railwayignore) keeps it out of what
`railway up` uploads, [.dockerignore](.dockerignore) out of what enters the image. The second
matters just as much: `.env` is indeed in `.gitignore`, but that protects the repository, not the
`COPY . .` of the build, and a secret baked into an image layer stays there long after the variable
has been changed.

Then declare the operator accounts (named identities, hashed passwords):

```bash
# generates one entry per operator
python admin_identity.py alice <password> admin
python admin_identity.py bob <password> operator

# group them into a JSON array
railway variable set JORDAN_ADMIN_USERS='[{"login":"alice","passwordHash":"pbkdf2:sha256:...","role":"admin"}]'
```

Or through the Railway dashboard: Project → Variables → Add variables

⚠️ `JORDAN_ADMIN_USERS` / `JORDAN_ADMIN_TOKEN` guard the whole `/jordan/admin/*` namespace (client
list, statuses, sending commands, deletion). Without at least one of the two, the server refuses
**every** admin request with a 401 — and since the Railway URL is public, never deploy without one.
Passwords are never stored in clear, only their hash.

Finally, close passive client registration, which is open by default:

```bash
railway variable set JORDAN_REGISTRATION_KEY=$(python -c "import secrets; print(secrets.token_hex(32))")
```

Without this variable, anyone who knows the URL can register a client. Passive programs then send
that key as `Authorization: Bearer <key>` when registering (the `registration_key` argument of
`jordan_py` / `jordan-client`, `jordan register --registration-key`, or the
`JORDAN_REGISTRATION_KEY` environment variable on the client side). Attempts are capped at
`JORDAN_REGISTRATION_RATE_LIMIT` per window of `JORDAN_REGISTRATION_RATE_WINDOW` seconds and per
caller address (20/minute by default), whether they succeed or not — Railway puts a proxy in front
of the container, so the address is read from `X-Forwarded-For`.

### Rotating the key without interrupting clients

A JSON object names several valid keys at once, which avoids having to update every client in the
same minute:

```bash
railway variable set JORDAN_REGISTRATION_KEY='{"retiring":"<old>","current":"<new>"}'
```

1. add the new key beside the old one, redeploy;
2. move the clients over, one at a time;
3. drop the `retiring` entry once `railway logs` stops naming it.

Every accepted registration logs the *name* of the key used, never the key — that is what makes
step 3 safe. The same mechanism gives one key per population (CI, laptops, a partner) so that only
one has to be revoked.

⚠️ a value that is set but unusable (invalid JSON, an empty object, an entry without a key or
without a name, an array) **stops the server from starting**, with the reason spelled out in the
logs. That is deliberate: ignoring the faulty entry would leave a key its operator believes valid,
silently refusing its clients. The `healthcheckPath` in [railway.json](railway.json) does the rest —
the faulty deployment never takes traffic, and the previous one keeps serving.

That last property has a cost worth knowing before you meet it: the URL keeps answering, from the
*previous* configuration, and the logs you see are that deployment's. A variable you have just set
can therefore look like it had no effect at all, while the explicit refusal sits in the log stream
of a deployment that is not running. Validate the value before setting it, on your own machine:

```bash
JORDAN_REGISTRATION_KEY='{"retiring":"<old>","current":"<new>"}' python jordan_server.py --check
```

`--check` runs the same validation the boot runs and starts nothing. It applies to
`JORDAN_ADMIN_USERS` and `REDIS_SSL` too — every setting whose failure mode is a container that
never takes traffic.

## Step 4: Deploy

```bash
# Option 1: through the CLI
cd server
railway up

# Option 2: through Git (recommended for CI/CD)
git add server/Dockerfile server/railway.json
git commit -m "Add Railway deployment configuration"
git push origin main
```

Railway detects the `Dockerfile` and builds it automatically.

### What the container runs

```dockerfile
RUN useradd --system --user-group --no-create-home jordan
USER jordan
CMD ["sh", "-c", "exec gunicorn api:app --bind 0.0.0.0:${PORT:-8080} --workers 2 ..."]
```

- **gunicorn, not `python jordan_server.py`** — the Flask development server is single-process and
  was never written to face a network. `JORDAN_DEBUG` has no effect at all under gunicorn, for that
  matter: there is no development server to put a debugger in.
- **`exec`** — gunicorn becomes PID 1 and receives the `SIGTERM` of a redeploy, so it drains
  in-flight requests instead of being killed. Without it the shell stays PID 1 and forwards nothing
  to its child. The `sh -c` is only there to expand `$PORT`, which Railway sets itself.
- **`USER jordan`** — the process owns none of the files it serves: the code and the interpreter
  stay owned by `root`, read-only. A flaw in the server then buys a shell that cannot modify the
  image it runs from.
- **no `startCommand` in [railway.json](railway.json)** — it would shadow the `CMD` without
  replacing it in the file you read. One start command, in the `Dockerfile`, rather than two that
  diverge the day one of them is fixed.

## Step 5: Check the deployment

```bash
# see the logs
railway logs

# see the public URL
railway open
# The API will be available at: https://your-project-xyz.up.railway.app/jordan
# Swagger UI: /jordan/swagger-ui, only if JORDAN_ENABLE_DOCS=true — otherwise 404,
# like /jordan/swagger.json. A public deployment has no reason to publish the
# complete map of its API; leave it off, and turn it on for the length of a test.
```

Check the region. How is it changed?
Check the port in Settings/Public Networking: it must match the one the container exposes. Does not
work well with 5000. Better with 8080.

## Checklist before going live

The Railway URL is HTTPS, but it is public and guessable: it protects nothing, it carries.
**Authentication is the only real barrier** — everything below follows from that.

| | Check | How |
|---|---|---|
| ☐ | `/jordan/admin/*` is guarded | `curl https://<url>/jordan/admin/clients` → `401`, never the list |
| ☐ | The admin token is not the development one | `railway variable list`: `JORDAN_ADMIN_TOKEN` randomly generated, ≠ the `jordan_dev_admin_token` of `docker-compose.yml` |
| ☐ | Named operators exist | `JORDAN_ADMIN_USERS` declared, one account per person, the narrowest role that works (`viewer` by default) |
| ☐ | Registration is closed | `curl -X POST https://<url>/jordan/client/register -d '{}' -H 'Content-Type: application/json'` → `401` |
| ☐ | The debugger is off | `JORDAN_DEBUG` not declared (and without effect under gunicorn anyway) |
| ☐ | The documentation is not published | `curl -o /dev/null -w '%{http_code}' https://<url>/jordan/swagger.json` → `404`, not just `/swagger-ui` |
| ☐ | The Redis link is encrypted, or its absence is a decision | `REDIS_SSL=true` and `railway logs` shows `Redis connection is encrypted`. On the free RedisCloud plan TLS does not exist: leave it off, and treat what the deployment stores as readable in transit (see step 3) |
| ☐ | No secret sits in a versioned file | `git grep -nE '(REDIS_PASSWORD\|JORDAN_ADMIN_TOKEN\|JORDAN_REGISTRATION_KEY)=[A-Za-z0-9]{16,}' -- server` returns nothing (see below) |
| ☐ | The container does not run as root | `docker build -t jordan-server . && docker run --rm jordan-server whoami` → `jordan` (`railway ssh whoami` against the deployment, if the plan allows it) |
| ☐ | The health check answers | `curl https://<url>/jordan/hello` → `200` |

The secret scan matches a long unbroken run of letters and digits, which is what a generated secret
looks like and what the placeholders in these files deliberately are not — they all carry a hyphen
or an underscore (`your-redis-password`, `test_password`). Grepping for the variable names alone
returns every example in this guide, and a check that always fires is a check nobody reads.
`git grep` also searches tracked files only, which is the right scope: an untracked `server/.env`
on a laptop is not the problem, a secret committed is.

The first few lines of the startup logs summarise the state of the server on their own — this is
what `log_configuration()` writes at import, gunicorn included:

```
[INFO] 2 admin operator account(s) declared in JORDAN_ADMIN_USERS
[INFO] 1 registration key(s) accepted: current
[INFO] JORDAN_ENABLE_DOCS is off: neither Swagger UI nor the OpenAPI spec behind it is served
[INFO] Redis connection is encrypted
```

An `[ERROR] Neither JORDAN_ADMIN_TOKEN nor JORDAN_ADMIN_USERS is set` line at startup means the
admin namespace refuses everything: the deployment is unusable rather than open — but it should not
be left in that state.

## Rotating secrets

Nothing here expires on its own except operator sessions. What follows is therefore manual, and
each secret has its own way of being replaced without downtime.

| Secret | Effect of changing it | Procedure |
|---|---|---|
| `JORDAN_REGISTRATION_KEY` | no existing client is affected — the key only opens `register` | rotation without downtime, [see above](#rotating-the-key-without-interrupting-clients) |
| `JORDAN_ADMIN_TOKEN` | machine-to-machine callers carrying it get a `401` on their next call | generate, update the variable, redeploy, then update the scripts. Sessions opened through `/admin/login` are not affected |
| An operator's password | only that account is touched | `python admin_identity.py <login> <new> <role>`, replace its entry in `JORDAN_ADMIN_USERS`, redeploy |
| `REDIS_PASSWORD` | downtime: the old and the new do not coexist | change it on the RedisCloud side, update the variable, redeploy. A few seconds of `500` in between |
| An operator session token | expires on its own after `JORDAN_ADMIN_SESSION_TTL` | nothing to do; `POST /admin/logout` to revoke one immediately |

Two things to know before starting:

- **a secret that has been deployed cannot be unsaid** — it stayed in the build logs, in the Railway
  variable history, and in the shell of whoever typed it. "Changed" is the only reachable state;
  "never disclosed" no longer is. That is the reason for the "How" column of the checklist: better
  to verify that a secret never touched a versioned file than to have to take it back out.
- **a redeploy is required** — variables are read when the module is imported, so at process start.
  Changing a variable without redeploying changes nothing for the running server (Railway does
  redeploy on a variable change, but check the logs to confirm it).

A secret suspected of having leaked is changed in reverse order of its reach: first
`JORDAN_ADMIN_TOKEN` and the operator passwords — they grant reading every client and deleting the
base — then `REDIS_PASSWORD`, and last the registration key, which only allows creating more
clients.

## Estimated costs

| Component | Cost |
|---|---|
| Railway container (512MB) | ~$2-3/month |
| RedisCloud (≤30MB) | Free |
| **Total** | **~$2-3/month** |

## Environment variables

A complete example for Railway:

```
REDIS_HOST=your-instance.rediscloud.com
REDIS_PORT=12345
REDIS_PASSWORD=your-secure-password
REDIS_SSL=true    # only on a plan that offers TLS; omit on the free RedisCloud tier
JORDAN_ADMIN_USERS=[{"login":"alice","passwordHash":"pbkdf2:sha256:...","role":"admin"}]
JORDAN_ADMIN_TOKEN=your-64-hex-char-admin-token
JORDAN_ADMIN_SESSION_TTL=43200
JORDAN_REGISTRATION_KEY=your-64-hex-char-registration-key
JORDAN_REGISTRATION_RATE_LIMIT=20
JORDAN_REGISTRATION_RATE_WINDOW=60
```

`JORDAN_DEBUG` and `JORDAN_ENABLE_DOCS` are absent from that list: both are off until declared, and
that is what is wanted here. The Werkzeug debugger is a Python console offered to whoever reaches
the URL — public and over HTTPS, but public — and the OpenAPI spec is the complete map of the API.
Turning them on for Railway is done for the length of a test, never permanently.

## Troubleshooting

### Error: "Cannot connect to Redis"
- Check that REDIS_HOST, REDIS_PORT and REDIS_PASSWORD are correct
- Check that RedisCloud allows external connections
- A TLS handshake error (`SSLError`, `wrong version number`): `REDIS_SSL=true` while TLS is not
  enabled on the database — enable it in the RedisCloud console, on the same endpoint. On the free
  30MB plan there is no toggle to enable: that plan has no TLS, and the variable has to come back
  off. The server never falls back to clear text on its own
- `SSLCertVerificationError` / `unable to get local issuer certificate`: the database presents one
  of Redis Cloud's self-signed roots rather than the GlobalSign chain, so the system CA store
  cannot verify it. Passing `redis_ca.pem` to the client is not supported yet — open an issue
  rather than turning verification off
- Conversely, an immediate `ConnectionError` with `REDIS_SSL` absent on a database that requires
  TLS: declare the variable

### The container does not start: `REDIS_SSL='...' is not a boolean`
The variable holds something other than `true`/`false` (`1`/`0`, `yes`/`no`, `on`/`off` work too).
Unlike `JORDAN_DEBUG`, an unreadable value stops the server rather than falling back to the default:
that default is the clear-text connection, and a typo must not be enough to send the Redis password
across the network with nothing saying so.

### The logs say `Neither JORDAN_ADMIN_TOKEN nor JORDAN_ADMIN_USERS is set` — but I can see it in `railway variables list`

Those two statements are about different things. `railway variables list` shows the variables the
service *will* pass to its next deployment; that log line was written by the process **currently
answering**, which received none.

The usual cause is a variable the running deployment never saw, because the deployment that carried
it refused to start and Railway kept the previous one serving. Look for the reason in the failed
deployment's logs — it is spelled out:

```
jordan_constants.ConfigurationError: JORDAN_ADMIN_USERS holds a single account rather than
an array - a lone account is still an array, so wrap it in brackets: [{"login": "alice", ...}]
```

That one is the common slip: a single operator to declare, so the entry gets pasted without its
brackets. `python admin_identity.py <login> <password> <role>` prints the complete bracketed value
first, for exactly that reason — paste that line, not the entry under it.

The general shape of the trap is worth remembering: **the deployment you are reading the logs of is
not the one that has your new variable.** A configuration error stops the boot on purpose, so the
symptom you see is always the *previous* configuration still running.

Two habits make it a non-event:

```bash
# before setting anything, on your machine, with the value you are about to use
JORDAN_ADMIN_USERS='[{"login": "alice", "passwordHash": "...", "role": "operator"}]' \
  python jordan_server.py --check

# after setting it, confirm the new deployment actually took over
railway status
```

Checking the variable is listed is not the same as checking it is running.

### Every `/jordan/admin/*` request returns 401
- Check that `JORDAN_ADMIN_USERS` or `JORDAN_ADMIN_TOKEN` is set on the server
  (`railway variable list`); otherwise the startup logs show an explicit error — but read the
  section just above first, since a set variable can still be absent from the running process
- Check that the client sends `Authorization: Bearer <token>` with an admin session token (returned
  by `POST /jordan/admin/login`) or the shared token — and not the token of a passive client, which
  only opens `/jordan/client/*`
- A session token expires after `JORDAN_ADMIN_SESSION_TTL` (12 h by default): log in again through
  `/jordan/admin/login`

### `POST /jordan/client/register` returns 401
`JORDAN_REGISTRATION_KEY` is set on the server: the client must send one of the declared keys as
`Authorization: Bearer <key>`. Check that it is not the token of an already registered client
(`authToken`), which only opens the other `/jordan/client/*` routes.

### The container does not start: `ConfigurationError`
`JORDAN_REGISTRATION_KEY` or `JORDAN_ADMIN_USERS` is set but unusable; the message names the faulty
entry (a key without a name, an account without a `passwordHash`, an unknown role, a login declared
twice, invalid JSON). That is deliberate: ignoring the faulty entry would leave a key or an account
its operator believes valid. Fix the variable and redeploy — until then, the previous deployment
stays online.

### `POST /jordan/client/register` returns 429
Too many attempts from the same address within the current window (20/minute by default). Wait for
the window to close, or raise `JORDAN_REGISTRATION_RATE_LIMIT` if several legitimate clients
register in a burst from a single network exit.

### A `/jordan/admin/*` request returns 403
The token is valid but the operator's role lacks the permission: `viewer` only reads, `operator` can
also send messages, only `admin` can delete.

### The app restarts in a loop
```bash
railway logs  # see the errors
```

### Changing the environment variables
```bash
railway variable list           # see the current ones
railway variable update REDIS_PASSWORD=new-password
```

## Scaling

If you exceed 30MB on RedisCloud:
- Paid RedisCloud plan (~$15/month for 250MB)
- Or use Redis-on-Railway (less stable, but included)

## Monitoring

The Railway dashboard shows:
- CPU/RAM usage
- Real-time logs
- Recent deployments
- Service health

Useful links:
- https://docs.railway.app/deploy/dockerfiles
- https://docs.railway.app/reference/environment-variables
