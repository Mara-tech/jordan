"""How metric statuses are stored and read back as series, against a stand-in
for the Redis client.

A metric status is a status like any other — kept in the task's log, removed
with the task — plus an entry in a second list, per task of its lineage, which
is what a curve is read from.
"""

from unittest.mock import MagicMock

import pytest

# imported after conftest, which patches redis.Redis before any server module loads
import rejson_interface as storage  # noqa: E402

ROOT_ID, CHILD_ID = 7, 8

TASKS = {
    ROOT_ID: {'taskId': ROOT_ID, 'name': 'training', 'tasks': [CHILD_ID]},
    CHILD_ID: {'taskId': CHILD_ID, 'name': 'fine_tune', 'parentTaskId': ROOT_ID, 'tasks': []},
}


@pytest.fixture
def rj(monkeypatch):
    """A fresh stand-in per test: the module-level one is shared by the suite."""
    fake = MagicMock()
    fake.json.return_value.get.side_effect = lambda key, *path: dict(TASKS[key]) if key in TASKS else None
    fake.exists.return_value = False
    monkeypatch.setattr(storage, 'rj', fake)
    return fake


def _pushed(rj):
    """(list key, status id) pairs pushed through the pipeline."""
    return [c.args for c in rj.pipeline.return_value.lpush.call_args_list]


def _status(status_id, task_id, name, value, step=None, timestamp=1000):
    metric = {'name': name, 'value': value}
    if step is not None:
        metric['step'] = step
    return {'statusId': status_id, 'type': 'metric', 'status': f'{name} = {value}', 'timestamp': timestamp,
            'parentTask': {'taskId': task_id, 'name': TASKS[task_id]['name']}, 'metric': metric}


# ── Writing ───────────────────────────────────────────────────────────────────


def test_metric_is_indexed_for_its_task_and_every_ancestor(rj):
    status_id = storage.post_status(CHILD_ID, {'type': 'metric', 'status': 'loss = 1',
                                               'metric': {'name': 'loss', 'value': 1}})['statusId']
    assert _pushed(rj) == [
        ('8_status', status_id), ('8_metrics', status_id),
        ('7_status', status_id), ('7_metrics', status_id),
    ]


@pytest.mark.parametrize('status_type', ['general', 'success', 'failure', 'progress'])
def test_other_statuses_stay_out_of_the_metric_index(rj, status_type):
    status_id = storage.post_status(CHILD_ID, {'type': status_type, 'status': 'working'})['statusId']
    assert _pushed(rj) == [('8_status', status_id), ('7_status', status_id)]


# ── Reading ───────────────────────────────────────────────────────────────────


def test_series_are_grouped_by_task_and_name():
    series = storage.group_metric_series([
        _status(1, CHILD_ID, 'held-out loss', 0.6653, step=0),
        _status(2, CHILD_ID, 'training loss', 0.7772, step=1),
        _status(3, CHILD_ID, 'held-out loss', 0.6677, step=1),
        _status(4, ROOT_ID, 'held-out loss', 0.9, step=1),
    ])
    assert [(s['parentTask']['taskId'], s['name']) for s in series] == [
        (CHILD_ID, 'held-out loss'), (CHILD_ID, 'training loss'), (ROOT_ID, 'held-out loss'),
    ]
    assert series[0]['points'] == [
        {'statusId': 1, 'value': 0.6653, 'timestamp': 1000, 'step': 0},
        {'statusId': 3, 'value': 0.6677, 'timestamp': 1000, 'step': 1},
    ]


def test_a_point_without_step_has_none_rather_than_a_guess():
    points = storage.group_metric_series([_status(1, ROOT_ID, 'throughput', 120, timestamp=1060)])[0]['points']
    assert points == [{'statusId': 1, 'value': 120, 'timestamp': 1060}]


def test_step_zero_is_a_step():
    points = storage.group_metric_series([_status(1, ROOT_ID, 'loss', 1, step=0)])[0]['points']
    assert points[0]['step'] == 0


def test_series_carries_the_latest_snapshot_of_its_task():
    first, last = _status(1, ROOT_ID, 'loss', 1), _status(2, ROOT_ID, 'loss', 0.5)
    last['parentTask']['state'] = 'COMPLETE'
    assert storage.group_metric_series([first, last])[0]['parentTask']['state'] == 'COMPLETE'


def test_read_metrics_puts_points_in_the_order_received(rj):
    # lpush keeps the newest first, which is the order Redis hands them back in
    rj.lrange.return_value = [b'3', b'2', b'1']
    rj.json.return_value.mget.return_value = [
        _status(3, ROOT_ID, 'loss', 0.3), None, _status(1, ROOT_ID, 'loss', 0.9),
    ]
    series = storage.read_metrics(ROOT_ID)
    rj.lrange.assert_called_once_with('7_metrics', 0, storage.MAX_METRIC_POINTS - 1)
    # a status deleted with its sub-task leaves a dangling id: skipped, not drawn
    assert [p['value'] for p in series[0]['points']] == [0.9, 0.3]


def test_read_metrics_of_a_task_without_any(rj):
    rj.lrange.return_value = []
    assert storage.read_metrics(ROOT_ID) == []


# ── Deleting ──────────────────────────────────────────────────────────────────


def test_deleting_a_task_deletes_its_metric_index(rj):
    rj.lrange.return_value = []
    rj.json.return_value.mget.return_value = [dict(TASKS[CHILD_ID])]
    to_delete = []
    storage.recursive_delete_tasks(dict(TASKS[ROOT_ID]), to_delete)
    assert '7_metrics' in to_delete
    assert '8_metrics' in to_delete
