"""How a progress status moves its task, against a stand-in for the Redis client.

The contract says a progress is an integer from 0 to 100, and the libraries
convert to one before sending, because the server copies nothing else into the
task: any other value is logged and moves nothing.
"""

from unittest.mock import MagicMock

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import rejson_interface as storage  # noqa: E402

TASK_ID = 8
TASK = {'taskId': TASK_ID, 'name': 'fine_tune', 'tasks': []}


@pytest.fixture
def rj(monkeypatch):
    """A fresh stand-in per test: the module-level one is shared by the suite."""
    fake = MagicMock()
    fake.json.return_value.get.side_effect = lambda key, *path: dict(TASK) if key == TASK_ID else None
    fake.exists.return_value = False  # every generated status id is free
    monkeypatch.setattr(storage, 'rj', fake)
    return fake


def _task_writes(rj):
    """(path, value) pairs written on the task itself through the pipeline."""
    return [c.args[1:] for c in rj.pipeline.return_value.json.return_value.set.call_args_list
            if c.args[0] == TASK_ID]


@pytest.mark.parametrize('percent', [0, 42, 100])
def test_an_integer_moves_the_task(rj, percent):
    storage.post_status(TASK_ID, {'type': 'progress', 'status': percent})
    assert _task_writes(rj) == [('.progress', percent), ('.state', storage.TASK_STATE_RUNNING)]


@pytest.mark.parametrize('status', [0.65, 42.0, '42', '42%', True])
def test_anything_else_is_only_logged(rj, status):
    storage.post_status(TASK_ID, {'type': 'progress', 'status': status})
    assert _task_writes(rj) == []
