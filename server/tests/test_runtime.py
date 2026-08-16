"""How much of itself the server exposes: the Werkzeug debugger, and the map of
the API published behind Swagger UI.

Both are settings a deployment has to opt into. The route tests run a fresh
interpreter rather than reloading the module in place, because flask-restx
decides which routes exist when it is handed the app: the decision is made once
per process, which is exactly what has to be tested.
"""

import json
import os
import subprocess
import sys
from pathlib import Path

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import api  # noqa: E402
from jordan_constants import JORDAN_OPEN_API_DOC_SUFFIX  # noqa: E402

SERVER_DIR = Path(__file__).resolve().parent.parent

# the spec Swagger UI reads: hiding the UI while serving this would hide nothing
SPEC_ROUTE = '/jordan/swagger.json'

_ROUTES_PROBE = (
    'import json, api; '
    "print('ROUTES=' + json.dumps(sorted(str(rule) for rule in api.app.url_map.iter_rules())))"
)


def _routes_of_a_fresh_server(**settings):
    """Routes a server started with these settings publishes.

    Both switches are passed explicitly, empty when a test does not set them, so
    that a developer's `server/.env` cannot change what is under test —
    python-dotenv leaves alone a variable that is already in the environment.
    Redis credentials are passed for the same reason: importing the module needs
    them, and nothing here connects."""
    env = dict(
        os.environ,
        REDIS_HOST='localhost',
        REDIS_PORT='6379',
        REDIS_PASSWORD='test_password',
        JORDAN_DEBUG='',
        JORDAN_ENABLE_DOCS='',
    )
    env.update(settings)
    started = subprocess.run([sys.executable, '-c', _ROUTES_PROBE], cwd=SERVER_DIR,
                             env=env, capture_output=True, text=True, check=False)
    # a refusal to start is reported as such, rather than as a missing route
    assert started.returncode == 0, started.stderr
    # the module reports its configuration at import, so pick our line out of it
    printed = [line for line in started.stdout.splitlines() if line.startswith('ROUTES=')]
    assert printed, started.stdout
    return json.loads(printed[0][len('ROUTES='):])


# ── What a deployment publishes ───────────────────────────────────────────────


def test_no_documentation_is_published_by_default():
    routes = _routes_of_a_fresh_server()
    assert JORDAN_OPEN_API_DOC_SUFFIX not in routes
    assert SPEC_ROUTE not in routes


def test_the_api_is_served_without_its_documentation():
    """Withholding the map is not withholding the API."""
    routes = _routes_of_a_fresh_server()
    assert '/jordan/hello' in routes
    assert '/jordan/client/register' in routes
    assert '/jordan/admin/clients' in routes


@pytest.mark.parametrize('asked_for', [{'JORDAN_ENABLE_DOCS': 'true'}, {'JORDAN_DEBUG': 'true'}])
def test_documentation_is_published_when_asked_for(asked_for):
    """Either explicitly, or by turning the debugger on — the same development
    mode, and the reason JORDAN_ENABLE_DOCS can stay unset on a laptop."""
    routes = _routes_of_a_fresh_server(**asked_for)
    assert JORDAN_OPEN_API_DOC_SUFFIX in routes
    assert SPEC_ROUTE in routes


def test_documentation_can_be_withheld_from_a_debug_server():
    routes = _routes_of_a_fresh_server(JORDAN_DEBUG='true', JORDAN_ENABLE_DOCS='false')
    assert JORDAN_OPEN_API_DOC_SUFFIX not in routes
    assert SPEC_ROUTE not in routes


# ── Reading the two switches ──────────────────────────────────────────────────


@pytest.fixture
def unset(monkeypatch):
    """Neither switch declared, the state of a deployment that says nothing."""
    monkeypatch.delenv('JORDAN_DEBUG', raising=False)
    monkeypatch.delenv('JORDAN_ENABLE_DOCS', raising=False)


def test_debug_is_off_unless_asked_for(unset):
    assert api.debug_enabled() is False


@pytest.mark.parametrize('value', ['1', 'true', 'TRUE', 'yes', 'on'])
def test_debug_reads_the_usual_spellings_of_yes(unset, monkeypatch, value):
    monkeypatch.setenv('JORDAN_DEBUG', value)
    assert api.debug_enabled() is True


@pytest.mark.parametrize('value', ['0', 'false', 'no', 'off', ' '])
def test_debug_reads_the_usual_spellings_of_no(unset, monkeypatch, value):
    monkeypatch.setenv('JORDAN_DEBUG', value)
    assert api.debug_enabled() is False


def test_an_unreadable_value_grants_nothing(unset, monkeypatch):
    """A misspelled 'true' is not a debugger handed to the network."""
    monkeypatch.setenv('JORDAN_DEBUG', 'oui')
    assert api.debug_enabled() is False


def test_an_unreadable_value_falls_back_to_the_default_rather_than_to_off(unset, monkeypatch):
    """Docs default to the debug flag, so that is what a typo falls back to."""
    monkeypatch.setenv('JORDAN_DEBUG', 'true')
    monkeypatch.setenv('JORDAN_ENABLE_DOCS', 'maybe')
    assert api.docs_enabled() is True


def test_docs_follow_debug_when_left_alone(unset, monkeypatch):
    assert api.docs_enabled() is False
    monkeypatch.setenv('JORDAN_DEBUG', 'true')
    assert api.docs_enabled() is True


def test_docs_can_be_published_without_the_debugger(unset, monkeypatch):
    monkeypatch.setenv('JORDAN_ENABLE_DOCS', 'true')
    assert api.docs_enabled() is True
    assert api.debug_enabled() is False


def test_docs_can_be_withheld_from_a_debug_server(unset, monkeypatch):
    monkeypatch.setenv('JORDAN_DEBUG', 'true')
    monkeypatch.setenv('JORDAN_ENABLE_DOCS', 'false')
    assert api.docs_enabled() is False


# ── Validating settings without starting a server ─────────────────────────────

# `jordan_server.py --check` exists because the alternative is finding out from a
# deployment: a bad setting stops the boot, the platform keeps the previous
# version serving, and its logs describe *that* configuration. The explicit
# refusal is written where nobody is looking, so it has to be reachable before
# deploying.


def _check_configuration_of(**settings):
    env = dict(
        os.environ,
        REDIS_HOST='localhost',
        REDIS_PORT='6379',
        REDIS_PASSWORD='test_password',
        REDIS_SSL='',
        JORDAN_DEBUG='',
        JORDAN_ENABLE_DOCS='',
        JORDAN_ADMIN_TOKEN='',
        JORDAN_ADMIN_USERS='',
        JORDAN_REGISTRATION_KEY='',
    )
    env.update(settings)
    return subprocess.run([sys.executable, 'jordan_server.py', '--check'], cwd=SERVER_DIR,
                          env=env, capture_output=True, text=True, check=False)


_ONE_ACCOUNT = '{"login": "alice", "passwordHash": "pbkdf2:sha256:1000$x$y", "role": "operator"}'


def test_check_accepts_a_usable_configuration():
    checked = _check_configuration_of(JORDAN_ADMIN_USERS=f"[{_ONE_ACCOUNT}]")
    assert checked.returncode == 0, checked.stderr
    assert 'Configuration is usable' in checked.stdout


def test_check_starts_no_server():
    """It has to return, not serve — otherwise it is not something you can put
    in front of a deployment."""
    checked = _check_configuration_of(JORDAN_ADMIN_USERS=f"[{_ONE_ACCOUNT}]")
    assert 'Starting API' not in checked.stdout


@pytest.mark.parametrize('broken, expected', [
    ({'JORDAN_ADMIN_USERS': _ONE_ACCOUNT}, 'wrap it in brackets'),   # the array forgotten
    ({'JORDAN_ADMIN_USERS': 'alice:secret'}, 'not valid JSON'),
    ({'JORDAN_REGISTRATION_KEY': '{"unnamed": ""}'}, 'carries no key'),
    ({'REDIS_SSL': 'oui'}, 'is not a boolean'),
])
def test_check_refuses_what_would_stop_a_deployment(broken, expected):
    """Every setting whose failure mode is a container that never takes traffic."""
    checked = _check_configuration_of(**broken)
    assert checked.returncode != 0
    assert expected in checked.stderr


def test_check_reports_error_messages_a_console_can_print():
    """These reach a Windows console through --check and a log stream through the
    boot. The project keeps them ASCII for that reason."""
    checked = _check_configuration_of(JORDAN_ADMIN_USERS=_ONE_ACCOUNT)
    assert checked.stderr.isascii()
