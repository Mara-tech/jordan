"""The bound both CLIs put on every request they send to the server.

``requests`` has no default timeout: a server that accepts the connection and then
stops answering holds a call forever, and a script waiting on ``jordan`` with it.
jordan_py sets none either (its callers choose theirs), so the CLI chooses here.
"""
import functools
from typing import Any, Callable, TypeVar, cast

import requests
import typer

REQUEST_TIMEOUT_ENV_VAR = "JORDAN_REQUEST_TIMEOUT"

# Generous on purpose: it has to let a server that is starting up (a platform
# waking a sleeping container) answer, and only has to stop one that never will.
DEFAULT_REQUEST_TIMEOUT = 30.0


def _positive(value: float) -> float:
    # 0 would not mean "no limit" to requests but an immediate failure, and a
    # negative value is refused by requests itself, in the middle of the command
    if value <= 0:
        raise typer.BadParameter("must be a number of seconds greater than 0")
    return value


REQUEST_TIMEOUT_OPTION = typer.Option(
    DEFAULT_REQUEST_TIMEOUT,
    "--request-timeout",
    envvar=REQUEST_TIMEOUT_ENV_VAR,
    callback=_positive,
    help="Seconds to wait for the server's answer on each request before giving up",
)

Command = TypeVar("Command", bound=Callable[..., Any])


def bounded(command: Command) -> Command:
    """Report a request that went unanswered as such, and exit 1, instead of the
    traceback requests raises. The command takes ``request_timeout``."""

    @functools.wraps(command)
    def run(*args: Any, **kwargs: Any) -> Any:
        try:
            return command(*args, **kwargs)
        except requests.exceptions.Timeout:
            typer.echo(
                f"No answer from the server within {kwargs['request_timeout']:g} s "
                "(--request-timeout).",
                err=True,
            )
            raise typer.Exit(1)

    return cast(Command, run)
