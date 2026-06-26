import json
import time as _time
from typing import List, Optional

import requests
import typer

admin_app = typer.Typer(help="Jordan admin CLI — manage passive clients from the command line.")

_SERVER_OPTION = typer.Option(
    None,
    "--server",
    envvar="JORDAN_SERVER",
    help="Jordan server base URL (e.g. http://localhost:5000/jordan/)",
)


def _base(server: Optional[str]) -> str:
    if not server:
        typer.echo("No server URL. Use --server or set JORDAN_SERVER.", err=True)
        raise typer.Exit(1)
    return server.rstrip("/") + "/"


@admin_app.command("list")
def list_clients(server: Optional[str] = _SERVER_OPTION) -> None:
    """List registered passive clients and their current state."""
    r = requests.get(_base(server) + "admin/clients")
    if r.status_code != 200:
        typer.echo(f"Error {r.status_code}: {r.text}", err=True)
        raise typer.Exit(1)
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
) -> None:
    """Send a message (action) to a passive client."""
    placeholders: dict = {}
    for p in param or []:
        if "=" not in p:
            typer.echo(f"Invalid param '{p}': expected key=value", err=True)
            raise typer.Exit(1)
        k, v = p.split("=", 1)
        placeholders[k] = v
    payload = {
        "author": "admin-cli",
        "action": {"actionName": action_name, "placeholders": placeholders},
    }
    r = requests.post(_base(server) + f"admin/{client_id}/message", json=payload)
    if r.status_code == 201:
        typer.echo(f"Message sent (id={r.json()})")
    else:
        typer.echo(f"Error {r.status_code}: {r.text}", err=True)
        raise typer.Exit(1)


@admin_app.command("watch")
def watch(
    client_id: int = typer.Argument(..., help="Client or task ID to watch"),
    interval: float = typer.Option(3.0, help="Polling interval in seconds"),
    lines: int = typer.Option(10, help="Number of status lines to fetch per poll"),
    server: Optional[str] = _SERVER_OPTION,
) -> None:
    """Stream status updates from a passive client (polling loop). Press Ctrl+C to stop."""
    base = _base(server)
    seen: set = set()
    typer.echo(f"Watching client {client_id}... (Ctrl+C to stop)")
    try:
        while True:
            r = requests.get(base + f"admin/{client_id}/status/{lines}")
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
) -> None:
    """Display the state machine audit trail for a message."""
    r = requests.get(_base(server) + f"admin/{message_id}")
    if r.status_code == 204:
        typer.echo(f"Message {message_id} not found.", err=True)
        raise typer.Exit(1)
    if r.status_code != 200:
        typer.echo(f"Error {r.status_code}: {r.text}", err=True)
        raise typer.Exit(1)
    typer.echo(json.dumps(r.json(), indent=2))


def main() -> None:
    admin_app()
