"""How the server connects to Redis: in clear text or under TLS.

The setting decides whether the Redis password — and every payload this server
stores — crosses the network readable. It is off by default, which is right for
a Redis on loopback and wrong for a managed one, so the interesting cases are
what happens when a deployment says nothing and what happens when it says
something unreadable.
"""

import os
import subprocess
import sys
from pathlib import Path

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import rejson_interface  # noqa: E402
from jordan_constants import ConfigurationError, REDIS_SSL_ENV_VAR  # noqa: E402

SERVER_DIR = Path(__file__).resolve().parent.parent

_CONNECTION_PROBE = (
    'import rejson_interface; '
    "print('CONNECTION=' + rejson_interface.rj.connection_pool.connection_class.__name__)"
)


def _connection_of_a_fresh_server(**settings):
    """Connection class the module builds under these settings.

    A fresh interpreter, because the client is built once at import: reloading
    the module in place would only tell us about a second one. Nothing connects
    — redis-py opens a socket on the first command, not on construction.

    Every variable the module reads is passed explicitly, so a developer's
    server/.env cannot change what is under test; python-dotenv leaves alone a
    variable already in the environment."""
    env = dict(
        os.environ,
        REDIS_HOST='localhost',
        REDIS_PORT='6379',
        REDIS_PASSWORD='test_password',
        REDIS_SSL='',
    )
    env.update(settings)
    return subprocess.run([sys.executable, '-c', _CONNECTION_PROBE], cwd=SERVER_DIR,
                          env=env, capture_output=True, text=True, check=False)


@pytest.fixture
def unset(monkeypatch):
    """No TLS setting declared — the state of a deployment that says nothing."""
    monkeypatch.delenv(REDIS_SSL_ENV_VAR, raising=False)


# ── Reading the setting ───────────────────────────────────────────────────────


def test_the_connection_is_in_clear_unless_asked_for(unset):
    """A default that suits loopback: turning TLS on by default would break every
    local stack against a Redis serving no certificate."""
    assert rejson_interface.redis_ssl_enabled() is False


@pytest.mark.parametrize('value', ['1', 'true', 'TRUE', 'yes', 'on'])
def test_tls_reads_the_usual_spellings_of_yes(unset, monkeypatch, value):
    monkeypatch.setenv(REDIS_SSL_ENV_VAR, value)
    assert rejson_interface.redis_ssl_enabled() is True


@pytest.mark.parametrize('value', ['0', 'false', 'no', 'off', ' '])
def test_tls_reads_the_usual_spellings_of_no(unset, monkeypatch, value):
    monkeypatch.setenv(REDIS_SSL_ENV_VAR, value)
    assert rejson_interface.redis_ssl_enabled() is False


def test_an_unreadable_value_is_refused_rather_than_ignored(unset, monkeypatch):
    """The opposite of how JORDAN_DEBUG treats a typo, and for the opposite
    reason: there the fallback withholds a debugger, here it would hand the
    Redis password to the network without a word."""
    monkeypatch.setenv(REDIS_SSL_ENV_VAR, 'oui')
    with pytest.raises(ConfigurationError) as refused:
        rejson_interface.redis_ssl_enabled()
    assert REDIS_SSL_ENV_VAR in str(refused.value)


# ── What the setting actually reaches ─────────────────────────────────────────


@pytest.mark.parametrize('asked_for, expected', [
    ({}, 'Connection'),
    ({'REDIS_SSL': 'false'}, 'Connection'),
    ({'REDIS_SSL': 'true'}, 'SSLConnection'),
])
def test_the_setting_reaches_the_connection(asked_for, expected):
    """redis_ssl_enabled() would be decoration if its result stopped at the
    function: what matters is the client the module ends up holding."""
    built = _connection_of_a_fresh_server(**asked_for)
    assert built.returncode == 0, built.stderr
    assert f"CONNECTION={expected}" in built.stdout


def test_a_broken_setting_stops_the_server():
    """Not merely the next Redis call: a server that answers its health check
    while talking to Redis in clear is the failure this refusal exists to
    prevent."""
    started = _connection_of_a_fresh_server(REDIS_SSL='oui')
    assert started.returncode != 0
    assert 'ConfigurationError' in started.stderr
    assert REDIS_SSL_ENV_VAR in started.stderr
