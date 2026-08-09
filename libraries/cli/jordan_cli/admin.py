import json
import os
import stat
import time as _time
from pathlib import Path
from typing import List, Optional

import requests
import typer

admin_app = typer.Typer(help="Jordan admin CLI — manage passive clients from the command line.")

ADMIN_TOKEN_ENV_VAR = "JORDAN_ADMIN_TOKEN"
ADMIN_PASSWORD_ENV_VAR = "JORDAN_ADMIN_PASSWORD"

# Sessions live in the home directory, not in the working directory like the
# passive client's .jordan_session: an operator is the same person in every
# directory, and a token that followed the shell around would be logged in here
# and logged out one 'cd' away.
SESSION_FILE = Path.home() / ".jordan_admin_session"

_SERVER_OPTION = typer.Option(
    None,
    "--server",
    envvar="JORDAN_SERVER",
    help="Jordan server base URL (e.g. http://localhost:5000/jordan/)",
)

_TOKEN_OPTION = typer.Option(
    None,
    "--token",
    envvar=ADMIN_TOKEN_ENV_VAR,
    help="Admin token to send instead of the stored session: a session token, or the "
         "server's shared bootstrap token (JORDAN_ADMIN_TOKEN)",
)


# ── session file ───────────────────────────────────────────────────────────────


def _read_sessions() -> dict:
    """Stored sessions, or nothing when the file is missing or unreadable.

    A damaged file is treated as no session at all: the worst it costs is one
    login, where refusing to run would block every command."""
    try:
        stored = json.loads(SESSION_FILE.read_text())
    except (OSError, ValueError):
        return {}
    return stored if isinstance(stored, dict) else {}


def _write_sessions(sessions: dict) -> None:
    """Rewrite the session file, readable by its owner alone.

    Opened through os.open rather than write_text: the mode has to be given when
    the file is created, otherwise the token spends a moment world-readable. The
    chmod afterwards covers a file created before, by an older version or a
    careless hand. (Windows honours neither, and the file inherits the ACL of
    the home directory.)"""
    handle = os.open(SESSION_FILE, os.O_CREAT | os.O_WRONLY | os.O_TRUNC, stat.S_IRUSR | stat.S_IWUSR)
    with os.fdopen(handle, "w") as session_file:
        session_file.write(json.dumps(sessions, indent=2))
    try:
        os.chmod(SESSION_FILE, stat.S_IRUSR | stat.S_IWUSR)
    except OSError:
        pass


def _store_session(base: str, session: dict) -> None:
    sessions = _read_sessions()
    servers = sessions.setdefault("servers", {})
    if not isinstance(servers, dict):
        servers = {}
        sessions["servers"] = servers
    servers[base] = {
        "token": session.get("token"),
        "login": session.get("login"),
        "role": session.get("role"),
        "permissions": session.get("permissions", []),
        "expiresAt": session.get("expiresAt"),
    }
    sessions["default"] = base
    _write_sessions(sessions)


def _forget_session(base: str) -> None:
    sessions = _read_sessions()
    servers = sessions.get("servers")
    if isinstance(servers, dict):
        servers.pop(base, None)
    if sessions.get("default") == base:
        sessions.pop("default", None)
    _write_sessions(sessions)


def _session_for(base: str) -> Optional[dict]:
    """Session opened against this server, if one is still valid.

    Sessions are kept per server URL for the same reason the Android app does
    it: a token is a credential of the server that issued it, and a mistyped
    --server must not hand it to whoever answers there."""
    servers = _read_sessions().get("servers")
    session = servers.get(base) if isinstance(servers, dict) else None
    if not isinstance(session, dict) or not session.get("token"):
        return None
    expires_at = session.get("expiresAt")
    if isinstance(expires_at, (int, float)) and expires_at <= _time.time():
        typer.echo(f"Session for {base} has expired.", err=True)
        return None
    return session


def _default_server() -> Optional[str]:
    default = _read_sessions().get("default")
    return default if isinstance(default, str) else None


# ── requests ───────────────────────────────────────────────────────────────────


def _base(server: Optional[str]) -> str:
    server = server or _default_server()
    if not server:
        typer.echo(
            "No server URL. Use --server, set JORDAN_SERVER, or run 'jordan-admin login'.",
            err=True,
        )
        raise typer.Exit(1)
    return server.rstrip("/") + "/"


def _headers(token: str) -> dict:
    return {"Authorization": "Bearer " + token}


def _auth(base: str, token: Optional[str]) -> str:
    """Token to send on an admin call: the one given on the command line (or in
    JORDAN_ADMIN_TOKEN), else the session opened against this very server."""
    if token:
        return token
    session = _session_for(base)
    if session:
        return session["token"]
    typer.echo(
        f"Not logged in on {base}. Run 'jordan-admin login', or pass --token / set "
        f"{ADMIN_TOKEN_ENV_VAR}.",
        err=True,
    )
    raise typer.Exit(1)


def _fail(response: requests.Response) -> None:
    """Report a refused call and stop. Says what to do about it: a bare
    '401: {"message": ...}' reads as a server problem, when it is a login."""
    if response.status_code == 401:
        typer.echo(
            "Unauthorized (401): the admin token is missing, invalid or expired. "
            "Run 'jordan-admin login'.",
            err=True,
        )
    elif response.status_code == 403:
        typer.echo(
            f"Forbidden (403): your role is not allowed to do this. {response.text.strip()}",
            err=True,
        )
    else:
        typer.echo(f"Error {response.status_code}: {response.text}", err=True)
    raise typer.Exit(1)


# ── session commands ───────────────────────────────────────────────────────────


def _describe(session: dict) -> str:
    permissions = ", ".join(session.get("permissions") or []) or "no permission"
    return f"{session.get('login')} ({session.get('role')}: {permissions})"


@admin_app.command("login")
def login_command(
    login: str = typer.Option(..., "--login", prompt=True, help="Operator login declared in JORDAN_ADMIN_USERS"),
    password: Optional[str] = typer.Option(
        None,
        "--password",
        envvar=ADMIN_PASSWORD_ENV_VAR,
        help=f"Operator password. Prompted for when absent, which keeps it out of the shell "
             f"history and the process list; ${ADMIN_PASSWORD_ENV_VAR} covers scripts",
    ),
    server: Optional[str] = _SERVER_OPTION,
) -> None:
    """Open an operator session and store its token for the other commands."""
    base = _base(server)
    if password is None:
        password = typer.prompt("Password", hide_input=True)
    response = requests.post(base + "admin/login", json={"login": login, "password": password})
    if response.status_code != 200:
        if response.status_code == 401:
            typer.echo("Invalid login or password.", err=True)
            raise typer.Exit(1)
        _fail(response)
    session = response.json()
    _store_session(base, session)
    expires_at = session.get("expiresAt")
    until = _time.strftime("%Y-%m-%d %H:%M", _time.localtime(expires_at)) if expires_at else "?"
    typer.echo(f"Logged in on {base} as {_describe(session)}, until {until}")


@admin_app.command("logout")
def logout(
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """Close the session on the server and drop the local token."""
    base = _base(server)
    response = requests.post(base + "admin/logout", headers=_headers(_auth(base, token)))
    # the local token goes either way: a session the server already dropped, or
    # one it never knew, is of no use here
    _forget_session(base)
    if response.status_code != 200:
        typer.echo(f"Local session dropped; server answered {response.status_code}.", err=True)
        raise typer.Exit(1)
    typer.echo(f"Logged out of {base}")


@admin_app.command("whoami")
def whoami(
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """Show the identity and permissions the server grants the current token."""
    base = _base(server)
    response = requests.get(base + "admin/me", headers=_headers(_auth(base, token)))
    if response.status_code != 200:
        _fail(response)
    typer.echo(_describe(response.json()))


# ── list ───────────────────────────────────────────────────────────────────────


@admin_app.command("list")
def list_clients(
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """List registered passive clients and their current state."""
    base = _base(server)
    r = requests.get(base + "admin/clients", headers=_headers(_auth(base, token)))
    if r.status_code != 200:
        _fail(r)
    clients = r.json()
    if not clients:
        typer.echo("No clients registered.")
        return
    for c in clients:
        typer.echo(f"[{c['clientId']}] {c['name']}  state={c['state']}")
        for t in c.get("tasks", []):
            typer.echo(
                f"  task [{t['taskId']}] {t['name']}"
                f"  state={t.get('state', '?')}"
                f"  progress={t.get('progress', '-')}"
            )


@admin_app.command("send")
def send(
    client_id: int = typer.Argument(..., help="Client or task ID to send the action to"),
    action_name: str = typer.Argument(..., help="Action name (must match one declared at registration)"),
    param: Optional[List[str]] = typer.Option(
        None, "--param", "-p", help="Action parameter as key=value (repeatable)"
    ),
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """Send a message (action) to a passive client."""
    placeholders: dict = {}
    for p in param or []:
        if "=" not in p:
            typer.echo(f"Invalid param '{p}': expected key=value", err=True)
            raise typer.Exit(1)
        k, v = p.split("=", 1)
        placeholders[k] = v
    # no author: the server takes it from the token, and ignores what the body says
    payload = {"action": {"actionName": action_name, "placeholders": placeholders}}
    base = _base(server)
    r = requests.post(
        base + f"admin/{client_id}/message", json=payload, headers=_headers(_auth(base, token))
    )
    if r.status_code != 201:
        _fail(r)
    typer.echo(f"Message sent (id={r.json()})")


@admin_app.command("watch")
def watch(
    client_id: int = typer.Argument(..., help="Client or task ID to watch"),
    interval: float = typer.Option(3.0, help="Polling interval in seconds"),
    lines: int = typer.Option(10, help="Number of status lines to fetch per poll"),
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """Stream status updates from a passive client (polling loop). Press Ctrl+C to stop."""
    base = _base(server)
    headers = _headers(_auth(base, token))
    seen: set = set()
    typer.echo(f"Watching client {client_id}... (Ctrl+C to stop)")
    try:
        while True:
            r = requests.get(base + f"admin/{client_id}/status/{lines}", headers=headers)
            # 204 is an empty history, not a failure; 401 means the session died
            # under the loop, and polling on would only repeat the refusal
            if r.status_code in (401, 403):
                _fail(r)
            if r.status_code == 200:
                for s in reversed(r.json()):
                    sid = s.get("statusId")
                    if sid not in seen:
                        seen.add(sid)
                        typer.echo(f"[{s.get('timestamp', '')}] [{s.get('type', '?')}] {s.get('status', '')}")
            _time.sleep(interval)
    except KeyboardInterrupt:
        pass


@admin_app.command("message-status")
def message_status(
    message_id: int = typer.Argument(..., help="Message ID"),
    server: Optional[str] = _SERVER_OPTION,
    token: Optional[str] = _TOKEN_OPTION,
) -> None:
    """Display the state machine audit trail for a message."""
    base = _base(server)
    r = requests.get(base + f"admin/{message_id}", headers=_headers(_auth(base, token)))
    if r.status_code == 204:
        typer.echo(f"Message {message_id} not found.", err=True)
        raise typer.Exit(1)
    if r.status_code != 200:
        _fail(r)
    typer.echo(json.dumps(r.json(), indent=2))


def main() -> None:
    admin_app()
