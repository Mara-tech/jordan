"""server/deploy.sh, the one path from an image to the Railway production service (JRD-26).

The script runs for real, under bash; every party it talks to — the registry through curl,
Railway through its CLI, git, Docker — is a stub placed first on the PATH, which answers from
files of the test's temporary directory and logs what it was asked.
"""

import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

import pytest

SCRIPT = Path(__file__).resolve().parent.parent / 'deploy.sh'
IMAGE = 'ghcr.io/mara-tech/jordan-server'
DIGEST = 'sha256:' + 'ab' * 32
OTHER_DIGEST = 'sha256:' + 'cd' * 32
COMMIT = '0123456789ab'

# what railway.json carried, now set on the service by every deployment — plus sleepApplication,
# without which Railway refuses any update of a free-plan service
SETTINGS = {
    'healthcheckPath': '/jordan/hello',
    'multiRegionConfig': {'europe-west4-drams3a': {'numReplicas': 1}},
    'sleepApplication': True,
}


def _bash():
    bash = shutil.which('bash')
    if bash and sys.platform == 'win32' and 'system32' in bash.lower():
        # WSL's bash, which would run the script in another filesystem; Git's sits next to git
        git = shutil.which('git')
        candidate = Path(git).parents[1] / 'bin' / 'bash.exe' if git else None
        bash = str(candidate) if candidate and candidate.exists() else None
    return bash


BASH = _bash()
pytestmark = pytest.mark.skipif(BASH is None, reason='needs bash')

STUBS = {
    'railway': r'''#!/usr/bin/env bash
printf 'railway %s\n' "$(echo "$*" | tr '\n' ' ')" >> "$STUB_DIR/calls"
case "$*" in
  *serviceInstanceUpdate*)
    for arg in "$@"; do
      case "$arg" in input=*) printf '%s' "${arg#input=}" > "$STUB_DIR/update_input.json" ;; esac
    done
    echo '{"data":{"serviceInstanceUpdate":true}}' ;;
  *serviceInstanceDeployV2*) echo '{"data":{"serviceInstanceDeployV2":"dep-1"}}' ;;
  *"deployment(id"*)
    # the n-th poll reads the n-th status, the last one repeating; appending is all it writes, where
    # rewriting the file in place can fail on Windows while a scanner holds it
    echo >> "$STUB_DIR/polls"
    status=$(sed -n "$(wc -l < "$STUB_DIR/polls")p" "$STUB_DIR/statuses")
    [ -n "$status" ] || status=$(tail -n 1 "$STUB_DIR/statuses")
    [ "$status" = UNREADABLE ] && exit 1
    printf '{"data":{"deployment":{"status":"%s","meta":{"imageDigest":"%s"}}}}\n' \
      "$status" "$DEPLOYED_DIGEST" ;;
  *) echo "unexpected railway call: $*" >&2; exit 1 ;;
esac
''',
    'curl': r'''#!/usr/bin/env bash
printf 'curl %s\n' "$*" >> "$STUB_DIR/calls"
case "$*" in
  *"/token?"*) echo '{"token":"anonymous"}' ;;
  *"/manifests/"*)
    [ -n "$REGISTRY_DIGEST" ] || exit 22
    printf 'HTTP/2 200\r\ndocker-content-digest: %s\r\n\r\n' "$REGISTRY_DIGEST" ;;
  *"/jordan/hello"*) exit "$HELLO_EXIT" ;;
  *) echo "unexpected curl call: $*" >&2; exit 1 ;;
esac
''',
    'git': r'''#!/usr/bin/env bash
printf 'git %s\n' "$*" >> "$STUB_DIR/calls"
case "$*" in
  *ls-remote*) cat "$STUB_DIR/remote_tags" ;;
  *"status --porcelain"*) cat "$STUB_DIR/dirty" ;;
  *"rev-parse --short=12 HEAD"*) echo 0123456789ab ;;
  *"rev-parse HEAD"*) echo 0123456789abcdef0123456789abcdef01234567 ;;
  *) echo "unexpected git call: $*" >&2; exit 1 ;;
esac
''',
    'docker': r'''#!/usr/bin/env bash
printf 'docker %s\n' "$*" >> "$STUB_DIR/calls"
[ "$1" = build ] && exit "$DOCKER_BUILD_EXIT"
exit 0
''',
}


class Run:
    def __init__(self, result, stub_dir):
        self.returncode = result.returncode
        self.stdout = result.stdout
        self.stderr = result.stderr
        calls = stub_dir / 'calls'
        self.calls = calls.read_text().splitlines() if calls.exists() else []
        update = stub_dir / 'update_input.json'
        self.update_input = json.loads(update.read_text()) if update.exists() else None

    def called(self, needle):
        return [i for i, call in enumerate(self.calls) if needle in call]

    def railway_calls(self):
        return [call for call in self.calls if call.startswith('railway ')]

    def probes(self):
        # curl only: the health check path is also in the settings sent to Railway
        return [i for i, call in enumerate(self.calls)
                if call.startswith('curl ') and '/jordan/hello' in call]


@pytest.fixture
def deploy(tmp_path):
    bin_dir = tmp_path / 'bin'
    bin_dir.mkdir()
    for name, body in STUBS.items():
        stub = bin_dir / name
        stub.write_text(body, newline='\n')
        stub.chmod(0o755)

    def run(*args, statuses=('SUCCESS',), registry_digest=DIGEST, deployed_digest=DIGEST,
            remote_tags=(), dirty='', hello_exit=0, docker_build_exit=0, deploy_timeout=600):
        (tmp_path / 'statuses').write_text('\n'.join(statuses) + '\n', newline='\n')
        (tmp_path / 'remote_tags').write_text(
            ''.join(f'{"0" * 40}\trefs/tags/server/v{tag}\n' for tag in remote_tags), newline='\n')
        (tmp_path / 'dirty').write_text(dirty, newline='\n')
        for leftover in ('calls', 'update_input.json', 'polls'):
            (tmp_path / leftover).unlink(missing_ok=True)
        env = {key: value for key, value in os.environ.items()
               if key not in ('RAILWAY_TOKEN', 'GITHUB_STEP_SUMMARY', 'IMAGE_REPOSITORY',
                              'RAILWAY_SERVICE_ID', 'RAILWAY_ENVIRONMENT_ID', 'JORDAN_URL')}
        env.update(
            PATH=str(bin_dir) + os.pathsep + os.environ['PATH'],
            STUB_DIR=tmp_path.as_posix(),
            PYTHON=Path(sys.executable).as_posix(),
            REGISTRY_DIGEST=registry_digest,
            DEPLOYED_DIGEST=deployed_digest,
            HELLO_EXIT=str(hello_exit),
            DOCKER_BUILD_EXIT=str(docker_build_exit),
            DEPLOY_TIMEOUT=str(deploy_timeout),
            POLL_INTERVAL='0',
        )
        # generous: every stub is a process, and spawning one under Git Bash on a loaded Windows
        # machine can take seconds
        result = subprocess.run([BASH, SCRIPT.as_posix(), *args], env=env,
                                capture_output=True, text=True, timeout=180)
        return Run(result, tmp_path)

    return run


# ── a release version ────────────────────────────────────────────────────────

@pytest.mark.parametrize('argument', ['1.2.0', 'v1.2.0'])
def test_a_version_is_deployed_with_the_settings_railway_json_carried(deploy, argument):
    run = deploy(argument)

    assert run.returncode == 0, run.stderr
    assert run.update_input == {**SETTINGS, 'source': {'image': f'{IMAGE}:1.2.0'}}
    assert run.called('manifests/1.2.0')


def test_the_image_is_set_before_the_deployment_is_asked_for(deploy):
    run = deploy('1.2.0')

    [update] = run.called('serviceInstanceUpdate')
    [deploy_call] = run.called('serviceInstanceDeployV2')
    assert update < deploy_call
    # a redeploy would repeat the previous deployment, image included
    assert not run.called('Redeploy')


def test_the_public_url_is_probed_once_the_deployment_is_live(deploy):
    run = deploy('1.2.0')

    [hello] = run.probes()
    assert hello > max(run.called('deployment(id'))
    assert 'https://jordan-production-5591.up.railway.app/jordan/hello' in run.calls[hello]


def test_latest_is_refused_before_anything_is_asked(deploy):
    run = deploy('latest')

    assert run.returncode != 0
    assert 'name the version' in run.stderr
    assert run.calls == []


def test_a_tag_missing_from_the_registry_leaves_the_service_untouched(deploy):
    run = deploy('9.9.9', registry_digest='')

    assert run.returncode != 0
    assert 'does not exist on the registry' in run.stderr
    assert run.railway_calls() == []


# ── waiting for the deployment ───────────────────────────────────────────────

def test_it_waits_until_the_deployment_takes_the_traffic(deploy):
    run = deploy('1.2.0', statuses=('QUEUED', 'BUILDING', 'UNREADABLE', 'DEPLOYING', 'SUCCESS'))

    assert run.returncode == 0, run.stderr
    assert len(run.called('deployment(id')) == 5
    assert 'deployment dep-1 is SUCCESS' in run.stdout


@pytest.mark.parametrize('status', ['FAILED', 'CRASHED', 'REMOVED'])
def test_a_deployment_railway_refused_fails_the_run(deploy, status):
    run = deploy('1.2.0', statuses=('DEPLOYING', status))

    assert run.returncode != 0
    assert f'ended {status}' in run.stderr
    assert 'the previous one keeps serving' in run.stderr
    assert not run.probes()


def test_a_deployment_that_never_settles_fails_at_the_deadline(deploy):
    run = deploy('1.2.0', statuses=('DEPLOYING',), deploy_timeout=0)

    assert run.returncode != 0
    assert 'still DEPLOYING after 0s' in run.stderr


def test_a_deployment_running_another_digest_fails_the_run(deploy):
    run = deploy('1.2.0', deployed_digest=OTHER_DIGEST)

    assert run.returncode != 0
    assert f'Railway runs {OTHER_DIGEST}' in run.stderr
    assert DIGEST in run.stderr


def test_an_unanswered_health_probe_fails_the_run(deploy):
    run = deploy('1.2.0', hello_exit=22)

    assert run.returncode != 0
    assert '/jordan/hello does not answer' in run.stderr


# ── the way back ─────────────────────────────────────────────────────────────

def test_release_deploys_the_highest_released_version(deploy):
    # in version order, not text order: 1.10.0 comes after 1.9.0
    run = deploy('--release', remote_tags=('1.9.0', '1.10.0', '1.2.3'))

    assert run.returncode == 0, run.stderr
    assert run.update_input['source'] == {'image': f'{IMAGE}:1.10.0'}


def test_release_with_nothing_released_stops(deploy):
    run = deploy('--release', remote_tags=())

    assert run.returncode != 0
    assert 'nothing has been released' in run.stderr
    assert run.railway_calls() == []


# ── a test from a workstation ────────────────────────────────────────────────

def test_build_pushes_the_commit_and_deploys_it(deploy):
    run = deploy('--build')

    assert run.returncode == 0, run.stderr
    [build] = run.called('docker build')
    [push] = run.called('docker push')
    assert f'-t {IMAGE}:test-{COMMIT}' in run.calls[build]
    assert '--platform linux/amd64' in run.calls[build]
    assert run.calls[push] == f'docker push {IMAGE}:test-{COMMIT}'
    assert build < push < min(run.called('railway '))
    assert run.update_input['source'] == {'image': f'{IMAGE}:test-{COMMIT}'}


def test_build_refuses_uncommitted_changes(deploy):
    run = deploy('--build', dirty=' M server/api.py\n')

    assert run.returncode != 0
    assert 'uncommitted changes' in run.stderr
    assert not run.called('docker ')
    assert run.railway_calls() == []


def test_an_image_that_does_not_build_is_neither_pushed_nor_deployed(deploy):
    run = deploy('--build', docker_build_exit=1)

    assert run.returncode != 0
    assert 'does not build' in run.stderr
    assert not run.called('docker push')
    assert run.railway_calls() == []
