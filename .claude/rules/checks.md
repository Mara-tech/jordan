# Local checks

What has to pass before a push, what a pull request runs, and what reports coverage. **This file is the project's, not the
installer's**: it starts with whatever the setup could read off the build files, and it is filled in
by the work itself — a ticket that adds a linter adds its line here, in the same pass.

| What | Command |
|---|---|
| before a push | the rows of [By module](#by-module) whose paths the branch touches — `git diff --name-only origin/main...HEAD` |
| on every pull request | `ci-python.yml`, `ci-java.yml`, `ci-android.yml` — **path-filtered**: a pull request touching none of their paths (docs, `.claude/`, `sample/`) registers no check, and that is expected |
| coverage | Python modules only — see [Coverage](#coverage); Java and Android measure none |

## How to use it

**Run what the table names, before every push.** Finding a failure here costs one minute; finding it
in CI costs a round trip.

**A line that says *not recorded yet* is a question, not a permission to skip.** Look for the answer
where it is written down — the build file (`package.json`, `pom.xml`, `build.sbt`, `pyproject.toml`,
`Makefile`…), the CI workflow, the README — run what you find, and **write it into the table in the
same pass**. Nothing to find, because the project has no tests yet? Say so in the report and leave
the line as it is: the ticket that brings the first test is the one that fills it.

**A command that no longer works is a bug in this file.** Fix the line rather than working around it,
and say in the report that you changed it.

**A check this table names that a pull request does not run is a divergence.** The remote check and
the local one answer the same question, and the cheapest way to keep them from drifting apart is for
the workflow to run the command named here rather than a list of its own. When it does keep its own
list, a line added above is a line to add there in the same pass — otherwise the check exists only on
the machine that just did the work, which is the one machine it cannot catch out.

Coverage has no line when the project measures none. That is a stated absence, and the report says
so — an absent section reads like an oversight.

## By module

Jordan is several projects in one repository, each with its own toolchain. Run the row of every
module the branch touches; a module no change reaches needs no run. The paths are the `paths:`
filters of the workflows — when one changes there, change it here.

Commands start from the repository root, in Git Bash, with `python` meaning the interpreter of the
[Python environment](#python-environment).

| Module | Touched paths | Command | CI job |
|---|---|---|---|
| Python lint | `server/`, `libraries/python/`, `libraries/cli/` | `python -m ruff check server/ libraries/python/ libraries/cli/` | `ci-python` / `lint` |
| Server | `server/` | `cd server && python -m pytest tests/` | `ci-python` / `test-server` |
| Server image | `server/Dockerfile`, `server/requirements.txt`, `server/.dockerignore`, a new file under `server/` | see [Server image](#server-image) | `ci-python` / `build-image` |
| `jordan_py` | `libraries/python/` | `python -m pytest libraries/python/` | `ci-python` / `test-library` |
| `jordan_cli` | `libraries/cli/`, and `libraries/python/` (it runs on `jordan_py`) | `python -m pytest libraries/cli/tests/` | `ci-python` / `test-cli` |
| Java libraries | `libraries/java/` | `cd libraries/java && ./gradlew test` | `ci-java` / `build-java` |
| Android app | `app/android/`, `libraries/java/jordan-core/` (included as a Gradle project) | `cd app/android && ./gradlew lint testDebugUnitTest assembleDebug` | `ci-android` / `build-android` |

A change to `libraries/java/jordan-core/` therefore runs both Java rows, and a change to
`libraries/prototype/contract.md` runs none — but a contract change is never alone: the modules that
implement it are in the same branch, and their rows run.

The Android row takes about two minutes on a warm Gradle daemon; it is still the one to run when the
app or `jordan-core` moved, since CI runs exactly that.

## Coverage

Measured with `coverage.py` on the three Python modules, test code excluded. Nothing measures the
Java libraries or the Android app (no JaCoCo is configured): a report touching only those says so
rather than leaving the section out.

| Module | Command |
|---|---|
| Server | `cd server && python -m coverage run --source=. --omit="tests/*" -m pytest tests/ -q && python -m coverage report` |
| `jordan_py` | `python -m coverage run --source=libraries/python/jordan_py/jordan_py --omit="*/test/*" -m pytest libraries/python/ -q && python -m coverage report` |
| `jordan_cli` | `python -m coverage run --source=libraries/cli/jordan_cli -m pytest libraries/cli/tests/ -q && python -m coverage report` |

`coverage report` prints one line per file and a `TOTAL` — the per-file before → after and the global
figure a report asks for. For the "before", run the same command on `main` (a worktree, or a stash)
before the change. The `.coverage` data files it leaves are git-ignored.

Coverage is not a CI check: `coverage` is installed locally only, and no threshold is enforced.

## Beyond the table

### Python environment

The three Python modules share one virtual environment at the repository root, `.venv`
(git-ignored). Created once:

```bash
python -m venv .venv
.venv/Scripts/python -m pip install -r server/requirements.txt pytest coverage ruff==0.15.22 \
  -e "libraries/python/jordan_py[test]" -e "libraries/cli[dev]"
```

Then `python` in the commands above is `.venv/Scripts/python` (Windows) or `.venv/bin/python`
(elsewhere) — the shell state does not persist between commands, so name it rather than relying on
an activation. The global interpreter lacks the server's dependencies: `server/tests` fails at
collection with `ModuleNotFoundError: No module named 'werkzeug'`.

`ruff` is pinned to the version `ci-python.yml` installs; bump both together. `ruff.toml` at the root
is its configuration. CI runs Python 3.11; a local 3.14 passes too, but a failure that only shows in
CI may be the version.

### Java toolchain

Both Gradle builds use the wrapper, which finds the JDK through `JAVA_HOME` — `java` need not be on
the `PATH`. CI uses Temurin 17; any JDK from 17 on builds locally. The Android build also reads
`app/android/local.properties` (`sdk.dir`, git-ignored), which Android Studio writes.

### Server image

What `ci-python` / `build-image` checks, reproducible when Docker is running:

```bash
docker build -t jordan-server:ci server/
test "$(docker run --rm jordan-server:ci whoami)" = jordan
docker run --rm jordan-server:ci sh -c 'test ! -e /app/.env'
```

The job then starts the container and probes `/jordan/hello`, `/jordan/admin/clients` (`401`) and
`/jordan/swagger.json` (`404`) — the full sequence is in the workflow. Run it when the change can
affect how the image is built or starts; a change inside `api.py` alone is covered by the server
tests.
