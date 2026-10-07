#!/usr/bin/env bash
# Deploy one image of the Jordan server to the Railway production service.
#
#   server/deploy.sh 1.2.0       the release image ghcr.io/mara-tech/jordan-server:1.2.0
#   server/deploy.sh --release   the last released version — the way back after a test or a bad release
#   server/deploy.sh --build     build this checkout, push it as test-<commit>, deploy that
#
# One path for the release workflow and for a workstation: they differ only by the tag they deploy
# (JRD-26). Railway never builds anything — it pulls the image named here, and the service
# settings that used to live in railway.json are set by this script on every deployment, since a
# service whose source is an image has no source tree to read that file from.
#
# Authentication is the `railway` CLI's: a workspace token in $RAILWAY_API_TOKEN when set (the
# release workflow), the session of `railway login` otherwise. This script never reads a token.
# Not a project token ($RAILWAY_TOKEN): Railway answers "Not Authorized" to serviceInstanceUpdate
# for one, and the CLI prefers it over any other credential — see the check below.
#
# Exit status: 0 once the deployment is live, runs the image the tag designates, and answers
# /jordan/hello; non-zero with the reason otherwise.

set -euo pipefail
# Without it a failure inside $(...) does not stop the function it runs: a failed `docker build`
# would go on to push, and deploy, whatever image last carried the tag. Bash 4.4 and later.
shopt -s inherit_errexit 2>/dev/null || true

IMAGE_REPOSITORY="${IMAGE_REPOSITORY:-ghcr.io/mara-tech/jordan-server}"
# Not secrets: an id is useless without a token. The project is `jordan`, the environment
# `production`, the only one there is.
RAILWAY_SERVICE_ID="${RAILWAY_SERVICE_ID:-cbe41815-ca64-488c-bbc8-8d43680ca435}"
RAILWAY_ENVIRONMENT_ID="${RAILWAY_ENVIRONMENT_ID:-b9569e5f-1860-4774-83d0-81726d76b31d}"
JORDAN_URL="${JORDAN_URL:-https://jordan-production-5591.up.railway.app}"
DEPLOY_TIMEOUT="${DEPLOY_TIMEOUT:-600}"
POLL_INTERVAL="${POLL_INTERVAL:-5}"

# What railway.json carried, and the only part of it Railway ever applied: its `restartPolicy` and
# `healthcheckInterval` are not Railway fields and were ignored. Without the health check a
# deployment that dies at boot takes the traffic; without the region it lands in Railway's default.
# sleepApplication is not a choice: on the free plan Railway refuses any update that does not
# restate it ("Free plan services must have sleepApplication set to true"). The service sleeps
# when idle, and the first request after that is slow while it wakes.
SERVICE_SETTINGS='{
  "healthcheckPath": "/jordan/hello",
  "multiRegionConfig": {"europe-west4-drams3a": {"numReplicas": 1}},
  "sleepApplication": true
}'

SERVER_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

die() {
  echo "deploy: $*" >&2
  exit 1
}

usage() {
  sed -n '2,6p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//' >&2
  exit 2
}

# A project token cannot point the service at another image: the first release refused with it
# (JRD-26). Set, it would also shadow a valid RAILWAY_API_TOKEN or `railway login` session, since
# the CLI uses it first — so it is refused here, before anything is asked of anyone.
[ -z "${RAILWAY_TOKEN:-}" ] \
  || die "RAILWAY_TOKEN is set: a project token, which Railway refuses for changing the service's image. Unset it, and authenticate with a workspace token in RAILWAY_API_TOKEN or with railway login"

# Any Python 3 reads JSON. On Windows `python3` and `python` are often the Microsoft Store stubs,
# which exist and run nothing, while `py`, the launcher, works; $PYTHON names one outright.
for candidate in "${PYTHON:-}" python3 python py; do
  if [ -n "$candidate" ] && "$candidate" -c 'import sys' >/dev/null 2>&1; then
    PYTHON="$candidate"
    break
  fi
done
[ -n "${PYTHON:-}" ] && "$PYTHON" -c 'import sys' >/dev/null 2>&1 || die "no working python on the PATH"

json_field() {
  # json_field <python expression on d> — reads JSON on stdin. The `tr`: Python on Windows ends
  # its lines with \r\n, and a \r kept in an id makes the next request ask for something else.
  "$PYTHON" -c 'import json, sys; d = json.load(sys.stdin); v = '"$1"'; print("" if v is None else v)' \
    | tr -d '\r'
}

# The digest the registry serves for <tag>, read anonymously: the package is public (JRD-22).
# Empty when the tag does not exist.
registry_digest() {
  local tag="$1" path="${IMAGE_REPOSITORY#*/}" registry="${IMAGE_REPOSITORY%%/*}" token
  token=$(curl -fsS "https://${registry}/token?scope=repository:${path}:pull" | json_field 'd["token"]') \
    || die "cannot read ${IMAGE_REPOSITORY} anonymously — is the package still public?"
  curl -fsSI \
    -H "Authorization: Bearer ${token}" \
    -H 'Accept: application/vnd.oci.image.index.v1+json, application/vnd.docker.distribution.manifest.list.v2+json, application/vnd.docker.distribution.manifest.v2+json, application/vnd.oci.image.manifest.v1+json' \
    "https://${registry}/v2/${path}/manifests/${tag}" 2>/dev/null \
    | tr -d '\r' | sed -n 's/^[Dd]ocker-[Cc]ontent-[Dd]igest: //p' || true
}

# The highest server/v* tag on origin — what was released, rather than what this clone has fetched.
latest_release() {
  local tags
  tags=$(git -C "$SERVER_DIR" ls-remote --tags --refs origin 'refs/tags/server/v*') \
    || die "cannot list the server/v* tags of origin"
  sed -n 's#.*refs/tags/server/v##p' <<<"$tags" | sort -V | tail -n 1
}

build_and_push() {
  local commit tag
  [ -z "$(git -C "$SERVER_DIR" status --porcelain -- .)" ] \
    || die "server/ has uncommitted changes: commit them first, so that test-<commit> says what runs"
  commit=$(git -C "$SERVER_DIR" rev-parse --short=12 HEAD)
  tag="test-${commit}"
  # linux/amd64 whatever the workstation: it is what Railway runs
  docker build --platform linux/amd64 \
    --label "org.opencontainers.image.source=https://github.com/Mara-tech/jordan" \
    --label "org.opencontainers.image.revision=$(git -C "$SERVER_DIR" rev-parse HEAD)" \
    -t "${IMAGE_REPOSITORY}:${tag}" "$SERVER_DIR" >&2 \
    || die "the image does not build"
  docker push "${IMAGE_REPOSITORY}:${tag}" >&2 \
    || die "push refused — log in first: docker login ghcr.io (a token with write:packages)"
  echo "$tag"
}

# The CLI reports a GraphQL error on stderr by its count only, and the message in the response it
# prints on stdout: that response is what explains a refusal, so it is not discarded.
railway_api() {
  local response
  if ! response=$(railway api --compact "$@"); then
    echo "deploy: Railway refused: ${response}" >&2
    return 1
  fi
  echo "$response"
}

[ $# -eq 1 ] || usage
case "$1" in
  --release)
    version=$(latest_release)
    [ -n "$version" ] || die "no server/v* tag on origin: nothing has been released"
    tag="$version"
    ;;
  --build) tag=$(build_and_push) ;;
  -h | --help) usage ;;
  latest) die "'latest' says nothing about what runs: name the version" ;;
  -*) usage ;;
  *) tag="${1#v}" ;;
esac
[[ "$tag" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]] || die "'$tag' is not an image tag"

image="${IMAGE_REPOSITORY}:${tag}"
expected_digest=$(registry_digest "$tag")
[ -n "$expected_digest" ] || die "${image} does not exist on the registry"
echo "deploy: ${image} (${expected_digest})"

# 1. Point the service at the image, with the settings railway.json used to carry.
input=$(IMAGE="$image" SETTINGS="$SERVICE_SETTINGS" "$PYTHON" -c '
import json, os
settings = json.loads(os.environ["SETTINGS"])
settings["source"] = {"image": os.environ["IMAGE"]}
print(json.dumps(settings))' | tr -d '\r')
railway_api 'mutation($serviceId: String!, $environmentId: String!, $input: ServiceInstanceUpdateInput!) {
  serviceInstanceUpdate(serviceId: $serviceId, environmentId: $environmentId, input: $input)
}' --raw-var "serviceId=${RAILWAY_SERVICE_ID}" --raw-var "environmentId=${RAILWAY_ENVIRONMENT_ID}" \
  --var "input=${input}" >/dev/null

# 2. Deploy it. Not a redeploy: serviceInstanceRedeploy repeats the previous deployment, image
# included, and would leave the new tag unused.
deployment_id=$(railway_api 'mutation($serviceId: String!, $environmentId: String!) {
  serviceInstanceDeployV2(serviceId: $serviceId, environmentId: $environmentId)
}' --raw-var "serviceId=${RAILWAY_SERVICE_ID}" --raw-var "environmentId=${RAILWAY_ENVIRONMENT_ID}" \
  | json_field 'd["data"]["serviceInstanceDeployV2"]')
[ -n "$deployment_id" ] || die "Railway returned no deployment"
echo "deploy: deployment ${deployment_id}"

# 3. Wait for it to take the traffic — or for Railway to refuse it, in which case the previous
# deployment keeps serving.
deadline=$((SECONDS + DEPLOY_TIMEOUT))
while :; do
  # a status that cannot be read is retried until the deadline: the deployment goes on without us
  if deployment=$(railway_api 'query($id: String!) { deployment(id: $id) { status meta } }' \
    --raw-var "id=${deployment_id}"); then
    status=$(json_field 'd["data"]["deployment"]["status"]' <<<"$deployment")
  else
    status="unreadable"
  fi
  case "$status" in
    SUCCESS | SLEEPING) break ;;
    FAILED | CRASHED | REMOVED | REMOVING | SKIPPED)
      die "deployment ${deployment_id} ended ${status} — the previous one keeps serving; see: railway logs --deployment ${deployment_id}" ;;
    NEEDS_APPROVAL) die "deployment ${deployment_id} waits for an approval in the Railway dashboard" ;;
  esac
  [ "$SECONDS" -lt "$deadline" ] || die "deployment ${deployment_id} still ${status} after ${DEPLOY_TIMEOUT}s"
  sleep "$POLL_INTERVAL"
done
echo "deploy: deployment ${deployment_id} is ${status}"

# 4. It runs the image the tag designated when we looked — not one re-pushed under it meanwhile.
deployed_digest=$(json_field '(d["data"]["deployment"]["meta"] or {}).get("imageDigest")' <<<"$deployment")
[ "$deployed_digest" = "$expected_digest" ] \
  || die "Railway runs ${deployed_digest:-an unknown digest}, but ${image} is ${expected_digest}"

# 5. And the public URL answers.
curl -fsS -o /dev/null --retry 5 --retry-delay 5 --retry-all-errors --max-time 60 "${JORDAN_URL}/jordan/hello" \
  || die "${JORDAN_URL}/jordan/hello does not answer"

echo "deploy: ${image} serves ${JORDAN_URL} (deployment ${deployment_id}, ${deployed_digest})"
if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  echo "Deployed \`${image}\` (\`${deployed_digest}\`) to ${JORDAN_URL} — Railway deployment \`${deployment_id}\`" \
    >>"$GITHUB_STEP_SUMMARY"
fi
