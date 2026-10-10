import socket
import threading
import time

import pytest
from typer.testing import CliRunner

from jordan_cli.request_timeout import REQUEST_TIMEOUT_ENV_VAR


@pytest.fixture(autouse=True)
def default_request_timeout(monkeypatch):
    """Tests expecting the default must not inherit the developer's own setting."""
    monkeypatch.delenv(REQUEST_TIMEOUT_ENV_VAR, raising=False)


@pytest.fixture
def silent_server():
    """Base URL of a server that accepts connections and never answers: what a
    hung server looks like to requests, which then waits for as long as it is
    told to — forever when it is told nothing."""
    listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    listener.bind(("127.0.0.1", 0))
    listener.listen(16)
    listener.settimeout(0.1)
    connections = []
    stop = threading.Event()

    def accept() -> None:
        while not stop.is_set():
            try:
                connection, _ = listener.accept()
            except TimeoutError:
                continue
            except OSError:
                return
            connections.append(connection)

    acceptor = threading.Thread(target=accept, daemon=True)
    acceptor.start()
    yield f"http://127.0.0.1:{listener.getsockname()[1]}/jordan/"
    stop.set()
    acceptor.join()
    # closing the connections also frees a command still waiting on one, when
    # the code under test hung and the test has already failed
    for connection in connections:
        connection.close()
    listener.close()


@pytest.fixture
def invoke_within():
    """Run a command in a thread and give it `seconds` to finish: a command that
    hangs fails the test instead of freezing the test run. Returns the result
    and the time it took."""

    def run(app, args, seconds):
        outcome = {}

        def invoke() -> None:
            outcome["result"] = CliRunner().invoke(app, args)

        started = time.monotonic()
        worker = threading.Thread(target=invoke, daemon=True)
        worker.start()
        worker.join(seconds)
        assert not worker.is_alive(), f"'{' '.join(args)}' still running after {seconds} s"
        return outcome["result"], time.monotonic() - started

    return run
