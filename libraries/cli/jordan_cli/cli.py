import json
import time
from pathlib import Path
from typing import Optional

import typer
from jordan_py import jordan

app = typer.Typer(help="Jordan CLI — use Jordan without writing Python code.")

SESSION_FILE = ".jordan_session"

_TASK_ID_OPTION = typer.Option(
    None, "--task-id", help="Sub-task ID to target (defaults to root task from session)"
)


def _load_session() -> dict:
    path = Path(SESSION_FILE)
    if not path.exists():
        typer.echo("No session found. Run 'jordan register' first.", err=True)
        raise typer.Exit(1)
    return json.loads(path.read_text())


def _make_instance(session: dict) -> jordan.JordanInstance:
    return jordan.JordanInstance(
        base_url=session["server"],
        task_id=session["taskId"],
        auth_token=session["authToken"],
        instance_name=session["name"],
    )


def _make_instance_for(session: dict, task_id: Optional[int]) -> jordan.JordanInstance:
    effective_task_id = task_id if task_id is not None else session["taskId"]
    return jordan.JordanInstance(
        base_url=session["server"],
        task_id=effective_task_id,
        auth_token=session["authToken"],
        instance_name=session["name"],
    )


@app.command()
def register(
    server: str = typer.Option(..., help="Jordan server base URL (e.g. http://localhost:5000/jordan/)"),
    name: str = typer.Option("default-client", help="Client name"),
) -> None:
    """Register with the Jordan server and save the session to .jordan_session."""
    instance = jordan.register(server, client_name=name)
    if instance is None:
        typer.echo("Registration failed. Check the server URL and try again.", err=True)
        raise typer.Exit(1)
    session = {
        "server": server,
        "taskId": instance.task_id,
        "authToken": instance.auth_token,
        "name": name,
    }
    Path(SESSION_FILE).write_text(json.dumps(session, indent=2))
    typer.echo(f"Registered as '{name}' (task_id={instance.task_id})")


@app.command("task-create")
def task_create(
    name: str = typer.Argument(..., help="Name of the sub-task"),
    task_id: Optional[int] = typer.Option(
        None, "--task-id", help="Parent task ID (defaults to root task from session)"
    ),
) -> None:
    """Create a sub-task under the root task (or a given parent). Prints the new task ID."""
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    sub = instance.create_task(name)
    if sub is None:
        typer.echo("Failed to create task.", err=True)
        raise typer.Exit(1)
    typer.echo(sub.task_id)


@app.command()
def status(
    message: str = typer.Argument(..., help="Status message"),
    type: str = typer.Option("general", help="Status type: general, progress, success, failure"),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Send a status update to the Jordan server."""
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    status_id = instance.send_status(message, status_type=type)
    if status_id:
        typer.echo(status_id)
    else:
        typer.echo("Failed to send status.", err=True)
        raise typer.Exit(1)


@app.command()
def progress(
    value: str = typer.Argument(..., help="Progress value (e.g. 42 or '42%')"),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Send a progress status update."""
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    status_id = instance.send_progress(value)
    if status_id:
        typer.echo(status_id)
    else:
        typer.echo("Failed to send progress.", err=True)
        raise typer.Exit(1)


def _print_message(msg: jordan.JordanMessage) -> None:
    output = {
        "messageId": msg.message_id,
        "actionName": msg.action_name,
        "placeholders": msg.placeholders.placehoders,
    }
    typer.echo(json.dumps(output, indent=2))


@app.command()
def action(
    wait: bool = typer.Option(False, "--wait", help="Block until an action is received"),
    timeout: int = typer.Option(60, help="Timeout in seconds when --wait is used"),
    interval: float = typer.Option(2.0, help="Polling interval in seconds when --wait is used"),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Read a pending action and print it as JSON. Acknowledges and marks it as received."""
    session = _load_session()
    instance = _make_instance_for(session, task_id)

    if wait:
        deadline = time.time() + timeout
        while time.time() < deadline:
            msg = instance.read_message()
            if msg:
                _print_message(msg)
                return
            time.sleep(interval)
        typer.echo("Timeout: no action received.", err=True)
        raise typer.Exit(1)
    else:
        msg = instance.read_message()
        if msg:
            _print_message(msg)
        else:
            typer.echo("No action pending.", err=True)
            raise typer.Exit(1)


@app.command()
def complete(
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Mark a task as complete.

    Without --task-id, marks the root task complete, unregisters the client,
    and deletes the session file. With --task-id, marks only that sub-task complete.
    """
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    instance.complete()
    if task_id is None:
        instance.unregister()
        Path(SESSION_FILE).unlink(missing_ok=True)
        typer.echo("Task complete.")
    else:
        typer.echo(f"Task {task_id} complete.")


@app.command()
def error(
    message: Optional[str] = typer.Argument(None, help="Optional error message"),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Mark a task as failed.

    Without --task-id, marks the root task failed, unregisters the client,
    and deletes the session file. With --task-id, marks only that sub-task as failed.
    """
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    if message:
        instance.send_failure_status(message)
    instance.update_task(jordan.TASK_STATE_ERROR)
    if task_id is None:
        instance.unregister()
        Path(SESSION_FILE).unlink(missing_ok=True)
        typer.echo("Task failed.")
    else:
        typer.echo(f"Task {task_id} failed.")


@app.command()
def unregister() -> None:
    """Unregister from the Jordan server and remove the local session."""
    session = _load_session()
    instance = _make_instance(session)
    ok = instance.unregister()
    if ok:
        Path(SESSION_FILE).unlink(missing_ok=True)
        typer.echo("Unregistered.")
    else:
        typer.echo("Unregister failed.", err=True)
        raise typer.Exit(1)


def main() -> None:
    app()
