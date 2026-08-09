# jordan_cli

Command-line interface for [Jordan](https://github.com/Mara-tech/jordan) — lets you use Jordan from shell scripts and pipelines without writing Python code.

## Installation

```bash
pip install jordan_cli
# or from source:
pip install -e libraries/cli
```

This installs two commands: `jordan` (passive-client CLI) and `jordan-admin` (operator/admin CLI).

## Quick start

```bash
# 1. Register with the server (creates a root task)
jordan register --server http://localhost:5000/jordan/

# 2. Send status updates during execution
jordan status "Starting data processing"
jordan progress "42"
jordan status "Done" --type success

# 3. Wait for an operator action (blocks up to 60 s)
jordan action --wait --timeout 60

# 4. Finish
jordan complete
```

## Session file

`jordan register` writes a `.jordan_session` file in the current directory. All subsequent commands read from this file — no need to pass credentials on every call.

Add `.jordan_session` to your `.gitignore`.

## Tasks

Every Jordan client is built around a **task hierarchy**. When you `jordan register`, the server creates a **root task** whose ID is stored in `.jordan_session`. All commands (`status`, `progress`, `action`, `complete`, `error`) operate on this root task by default.

For more granular tracking you can create **sub-tasks** with `jordan task-create` and target them with `--task-id`:

```bash
# Create two sub-tasks under the root; capture their IDs
TASK_A=$(jordan task-create "extract")
TASK_B=$(jordan task-create "load")

# Report progress per sub-task
jordan progress "10" --task-id "$TASK_A"
jordan progress "0"  --task-id "$TASK_B"

# Mark sub-tasks done independently (no unregister)
jordan complete --task-id "$TASK_A"
jordan complete --task-id "$TASK_B"

# Finish the root client
jordan complete
```

Sub-tasks can themselves be parents: pass `--task-id PARENT_ID` to `jordan task-create` to nest tasks further.

## Commands

### `jordan register`

```
jordan register --server URL [--name NAME] [--registration-key KEY]
```

Registers with the Jordan server and saves the session locally. The server creates a root task and returns its ID, which is stored in `.jordan_session`.

| Option | Default | Description |
|---|---|---|
| `--server` | *(required)* | Server base URL (e.g. `http://localhost:5000/jordan/`) |
| `--name` | `default-client` | Display name for this client |
| `--registration-key` | `$JORDAN_REGISTRATION_KEY` | Key required by servers that closed registration |

Registration is open on most servers. A server that set `JORDAN_REGISTRATION_KEY` answers `401` without the matching key, and `429` when too many attempts come from the same address in a short window. The key is only needed here — the session file holds the client token every other command uses, and never the key itself:

```bash
export JORDAN_REGISTRATION_KEY=<key>
jordan register --server https://your-server/jordan/ --name "nightly-export"
```

---

### `jordan task-create`

```
jordan task-create NAME [--task-id PARENT_ID]
```

Creates a sub-task and prints its task ID. By default the sub-task is created under the root task from the session. Pass `--task-id` to nest it under a different parent.

| Option | Default | Description |
|---|---|---|
| `--task-id` | root task | Parent task ID |

```bash
TASK_ETL=$(jordan task-create "etl-pipeline")
TASK_REPORT=$(jordan task-create "reporting" --task-id "$TASK_ETL")
```

---

### `jordan status`

```
jordan status MESSAGE [--type TYPE] [--task-id TASK_ID]
```

Sends a status update. Prints the `statusId` on success.

| Option | Default | Description |
|---|---|---|
| `--type` | `general` | One of: `general`, `progress`, `success`, `failure` |
| `--task-id` | root task | Target a specific sub-task |

---

### `jordan progress`

```
jordan progress VALUE [--task-id TASK_ID]
```

Shorthand for `jordan status VALUE --type progress`.

| Option | Default | Description |
|---|---|---|
| `--task-id` | root task | Target a specific sub-task |

```bash
jordan progress "75"
jordan progress "75%" --task-id 124
```

---

### `jordan action`

```
jordan action [--wait] [--timeout SECONDS] [--interval SECONDS] [--task-id TASK_ID]
```

Reads the next pending action from the server and prints it as JSON.

```json
{
  "messageId": "abc123",
  "actionName": "SEND_EMAIL",
  "placeholders": {
    "recipient": "user@example.com"
  }
}
```

| Option | Default | Description |
|---|---|---|
| `--wait` | `false` | Block until an action arrives |
| `--timeout` | `60` | Max wait time in seconds (used with `--wait`) |
| `--interval` | `2.0` | Polling interval in seconds (used with `--wait`) |
| `--task-id` | root task | Read an action sent to a specific sub-task |

Exits with code 1 if no action is pending (or timeout is reached).

**Shell script example — react on an action:**

```bash
result=$(jordan action --wait --timeout 120)
action=$(echo "$result" | python3 -c "import sys,json; print(json.load(sys.stdin)['actionName'])")

if [ "$action" = "SEND_REPORT" ]; then
    send_report.sh
fi
```

---

### `jordan complete`

```
jordan complete [--task-id TASK_ID]
```

Marks a task as complete.

- **Without `--task-id`**: marks the root task complete, unregisters the client, and deletes `.jordan_session`.
- **With `--task-id`**: marks only that sub-task complete. The session and root task are left untouched.

---

### `jordan error`

```
jordan error [MESSAGE] [--task-id TASK_ID]
```

Marks a task as failed, optionally sending an error status message.

- **Without `--task-id`**: marks the root task failed, unregisters the client, and deletes `.jordan_session`.
- **With `--task-id`**: marks only that sub-task failed. The session and root task are left untouched.

```bash
jordan error "Unexpected exit code from ffmpeg"
jordan error "Load step failed" --task-id 124
```

---

### `jordan unregister`

```
jordan unregister
```

Unregisters the root client from the server and deletes `.jordan_session` without changing any task state.

---

## Admin CLI (`jordan-admin`)

`jordan-admin` is the operator-side counterpart: it talks to the Jordan server's admin endpoints to monitor passive clients and send them actions. Every admin endpoint requires a token, so start with `jordan-admin login` — it stores the session and the server URL, and the other commands read both from there.

```bash
# Open a session (asks for the password, then remembers the token)
jordan-admin login --server http://localhost:5000/jordan/ --login bob

# List all registered clients and their sub-tasks
jordan-admin list

# Send an action to client 123 (or any sub-task ID)
jordan-admin send 123 SEND_EMAIL --param recipient=user@example.com

# Watch live status updates from task 124
jordan-admin watch 124

# Check the state machine history of message 456
jordan-admin message-status 456

# Close the session when done
jordan-admin logout
```

### Authentication

Every command accepts `--server URL` and `--token TOKEN`; both fall back to an environment variable (`JORDAN_SERVER`, `JORDAN_ADMIN_TOKEN`), and then to the session opened by `jordan-admin login`.

| Source | Used for |
|---|---|
| `jordan-admin login` | day-to-day operator work: a named identity, whose role the server enforces and whose login becomes the `author` of every message sent |
| `--token` / `$JORDAN_ADMIN_TOKEN` | scripts and CI: either a session token obtained elsewhere, or the server's shared bootstrap token (`JORDAN_ADMIN_TOKEN` in `server/.env`) |

`--token` wins over the stored session, so a script can override an interactive login without disturbing it.

```bash
# Unattended, with the shared bootstrap token
export JORDAN_SERVER=https://your-server/jordan/
export JORDAN_ADMIN_TOKEN=<shared-token>
jordan-admin list
```

Without a token, and without a session for that server, the command stops before calling: it says to log in rather than reporting a network error. `401` from the server says the same; `403` means the role is not allowed to do it (a `viewer` cannot `send`).

### Session file

`jordan-admin login` writes `~/.jordan_admin_session`, created readable by its owner alone (`0600` where the filesystem enforces modes). It is in the home directory, not the working directory like the passive client's `.jordan_session`: an operator is the same person in every directory, and a token that followed the shell around would be logged in here and logged out one `cd` away.

Tokens are stored **per server URL** and are never sent to another one: a token is a credential of the server that issued it, and a mistyped `--server` must not hand it to whoever answers there. The last server logged into becomes the default when neither `--server` nor `JORDAN_SERVER` is given.

The stored token expires on its own — the server sets the lifetime (`JORDAN_ADMIN_SESSION_TTL`, 12 h by default). Past that, commands ask for a new login. `jordan-admin logout` closes the session server-side and drops the local token.

### `jordan-admin login`

```
jordan-admin login --login NAME [--password PASSWORD] [--server URL]
```

Exchanges operator credentials for a session token and stores it. The password is prompted for when it is not passed, which keeps it out of the shell history and the process list; `$JORDAN_ADMIN_PASSWORD` covers unattended use.

| Option | Default | Description |
|---|---|---|
| `--login` | *(prompted)* | Operator login declared in the server's `JORDAN_ADMIN_USERS` |
| `--password` | *(prompted)* / `$JORDAN_ADMIN_PASSWORD` | Operator password |
| `--server` | `$JORDAN_SERVER`, else last logged-in server | Server base URL |

```
Logged in on http://localhost:5000/jordan/ as bob (operator: read, send), until 2026-08-09 21:14
```

---

### `jordan-admin logout`

```
jordan-admin logout [--server URL] [--token TOKEN]
```

Closes the session on the server (`POST /admin/logout`) and removes the local token. The local token is dropped even when the server refuses the call — a session it has already forgotten is of no use here.

---

### `jordan-admin whoami`

```
jordan-admin whoami [--server URL] [--token TOKEN]
```

Asks the server which identity and permissions the current token carries (`GET /admin/me`) — the quickest way to tell an expired session from a role that lacks a permission.

```
bob (operator: read, send)
```

---

### `jordan-admin list`

```
jordan-admin list [--server URL] [--token TOKEN]
```

Lists all registered passive clients with their sub-tasks and current states.

```
[123] my-script  state=REGISTERED
  task [124] extract  state=COMPLETE  progress=-
  task [125] load     state=RUNNING   progress=42
```

---

### `jordan-admin send`

```
jordan-admin send TASK_ID ACTION_NAME [-p key=value ...] [--server URL] [--token TOKEN]
```

Sends an action (message) to a task. `TASK_ID` can be the root client ID or any sub-task ID. The action name must match one declared by the client at registration. Requires the `send` permission — a `viewer` gets `403`.

| Option | Default | Description |
|---|---|---|
| `--param` / `-p` | *(none)* | Parameter as `key=value` (repeatable) |
| `--server` | `$JORDAN_SERVER`, else the session | Server base URL |
| `--token` | `$JORDAN_ADMIN_TOKEN`, else the session | Admin token |

The message's `author` is the authenticated operator: the server takes it from the token, so the CLI does not claim one.

```bash
jordan-admin send 123 SEND_REPORT
jordan-admin send 124 SEND_EMAIL -p recipient=ops@example.com -p subject="Alert"
```

Prints the assigned message ID on success.

---

### `jordan-admin watch`

```
jordan-admin watch TASK_ID [--interval SECONDS] [--lines N] [--server URL] [--token TOKEN]
```

Polls the server for new status updates from the given task and prints them as they arrive. Press Ctrl+C to stop. Works on both root tasks and sub-tasks. A session that expires under the loop stops it — polling on would only repeat the refusal.

| Option | Default | Description |
|---|---|---|
| `--interval` | `3.0` | Polling interval in seconds |
| `--lines` | `10` | Number of status lines fetched per poll |
| `--server` | `$JORDAN_SERVER`, else the session | Server base URL |
| `--token` | `$JORDAN_ADMIN_TOKEN`, else the session | Admin token |

```
Watching client 123... (Ctrl+C to stop)
[1751234567] [general] Starting data processing
[1751234570] [progress] 42
[1751234590] [success] Export complete
```

---

### `jordan-admin message-status`

```
jordan-admin message-status MESSAGE_ID [--server URL] [--token TOKEN]
```

Displays the raw JSON for a message, including its full state machine audit trail.

```json
{
  "messageId": 456,
  "action": { "actionName": "SEND_EMAIL", "placeholders": { "recipient": "ops@example.com" } },
  "audit": [
    { "timestamp": 1751234500, "state": "SERVER_RECEIVED" },
    { "timestamp": 1751234503, "state": "MESSAGE_DELIVERED" },
    { "timestamp": 1751234504, "state": "CLIENT_RECEIVED" },
    { "timestamp": 1751234510, "state": "MESSAGE_PROCESSED" }
  ]
}
```

---

## Full shell script example

```bash
#!/usr/bin/env bash
set -e

jordan register --server http://localhost:5000/jordan/ --name "nightly-export"

# Create sub-tasks for finer-grained tracking
TASK_EXTRACT=$(jordan task-create "extract")
TASK_LOAD=$(jordan task-create "load")

jordan status "Connecting to database" --task-id "$TASK_EXTRACT"
run_extract.sh && {
    jordan status "Extracted" --type success --task-id "$TASK_EXTRACT"
    jordan complete --task-id "$TASK_EXTRACT"
} || {
    jordan error "Extract failed" --task-id "$TASK_EXTRACT"
    jordan error "Pipeline aborted"
    exit 1
}

jordan status "Loading data" --task-id "$TASK_LOAD"
run_load.sh && {
    jordan status "Loaded" --type success --task-id "$TASK_LOAD"
    jordan complete --task-id "$TASK_LOAD"
} || {
    jordan error "Load failed" --task-id "$TASK_LOAD"
    jordan error "Pipeline aborted"
    exit 1
}

jordan complete
```
