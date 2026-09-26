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
    registration_key: Optional[str] = typer.Option(
        None,
        "--registration-key",
        envvar=jordan.REGISTRATION_KEY_ENV_VAR,
        help="Key required by servers that closed registration (JORDAN_REGISTRATION_KEY)",
    ),
) -> None:
    """Register with the Jordan server and save the session to .jordan_session.

    The registration key, when the server asks for one, is only needed here: the
    session file then holds the client token the other commands use.
    """
    trailing_slash_server = server.rstrip("/") + "/"
    instance = jordan.register(trailing_slash_server, client_name=name, registration_key=registration_key)
    if instance is None:
        typer.echo("Registration failed. Check the server URL and try again.", err=True)
        raise typer.Exit(1)
    session = {
        "server": trailing_slash_server,
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
    try:
        status_id = instance.send_status(message, status_type=type)
    except ValueError as e:  # a progress that is not a number from 0 to 100
        raise typer.BadParameter(str(e))
    if status_id:
        typer.echo(status_id)
    else:
        typer.echo("Failed to send status.", err=True)
        raise typer.Exit(1)


@app.command()
def progress(
    value: str = typer.Argument(..., help="Percentage from 0 to 100 (e.g. 42 or '42%'), sent as an integer"),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Send a progress status update: the task's progress bar in active clients."""
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    try:
        status_id = instance.send_progress(value)
    except ValueError as e:
        raise typer.BadParameter(str(e))
    if status_id:
        typer.echo(status_id)
    else:
        typer.echo("Failed to send progress.", err=True)
        raise typer.Exit(1)


def _number(text: str) -> float:
    """An int when the text is one, so '3' stays a step 3 rather than 3.0."""
    try:
        return int(text)
    except ValueError:
        pass
    try:
        return float(text)
    except ValueError:
        raise typer.BadParameter(f"'{text}' is not a number")


@app.command()
def metric(
    name: str = typer.Argument(..., help="Metric name, one curve per name (e.g. 'held-out loss')"),
    value: str = typer.Argument(..., help="The value (a negative one goes after --: jordan metric -- delta -0.5)"),
    step: Optional[str] = typer.Option(
        None, "--step", help="Progress point the value belongs to (an epoch, an iteration); placed in time when omitted"
    ),
    task_id: Optional[int] = _TASK_ID_OPTION,
) -> None:
    """Send a named value, drawn as a curve by active clients."""
    number = _number(value)
    step_number = _number(step) if step is not None else None
    session = _load_session()
    instance = _make_instance_for(session, task_id)
    status_id = instance.send_metric(name, number, step=step_number)
    if status_id:
        typer.echo(status_id)
    else:
        typer.echo("Failed to send metric.", err=True)
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
