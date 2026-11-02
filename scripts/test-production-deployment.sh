#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_root="$project_root/.dev-tmp/release-check"
mkdir -p "$scratch_root"
env_file="$(mktemp "$scratch_root/production.XXXXXX.env")"
chmod 600 "$env_file"
project="tandem-release-check"
edge_network="${project}-edge"
compose=(docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml")

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  docker network rm "$edge_network" >/dev/null 2>&1 || true
  rm -f "$env_file"
}
trap cleanup EXIT

test_secret="release-check-$(date +%s)-synthetic"
test_epoch="release-check-$(date +%s)"
cat > "$env_file" <<EOF
POSTGRES_DB=tandem_release_check
POSTGRES_USER=tandem_release_check
POSTGRES_PASSWORD=$test_secret
DATABASE_URL=postgresql://tandem_release_check:$test_secret@postgres:5432/tandem_release_check
CHANGE_FEED_EPOCH=$test_epoch
TRUST_PROXY=
TANDEM_EDGE_NETWORK=$edge_network
ACTUAL_SERVER_URL=
ACTUAL_SERVER_PASSWORD=
EOF

docker network create "$edge_network" >/dev/null
"${compose[@]}" config --quiet
"${compose[@]}" up --build --detach

wait_for_api() {
  for _ in $(seq 1 60); do
    if curl --silent --fail http://127.0.0.1:19754/api/v1/health/ready >/dev/null; then
      return 0
    fi
    sleep 1
  done
  "${compose[@]}" logs --no-color api postgres
  echo "Production stack did not become ready." >&2
  return 1
}
wait_for_api

pair_device() {
  local name="$1"
  local pairing_json code response token
  for _ in $(seq 1 5); do
    wait_for_api
    pairing_json="$("${compose[@]}" exec -T api node dist-cli/bin/pairing-cli.js --all-budgets --principal-id release-check)"
    code="$(sed -nE 's/.*"code"[[:space:]]*:[[:space:]]*"([A-Za-z0-9-]+)".*/\1/p' <<< "$pairing_json")"
    if [[ -z "$code" ]]; then
      echo "Production pairing CLI did not return a pairing code." >&2
      return 1
    fi
    if response="$(curl --silent --fail --request POST http://127.0.0.1:19754/api/v1/auth/pair \
      --header 'Content-Type: application/json' \
      --data "{\"code\":\"$code\",\"deviceName\":\"$name\"}" 2>/dev/null)"; then
      token="$(sed -nE 's/.*"accessToken"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/p' <<< "$response")"
      if [[ -n "$token" ]]; then
        printf '%s' "$token"
        return 0
      fi
    fi
    sleep 1
  done
  echo "Production pairing endpoint did not issue a device token for $name." >&2
  return 1
}

phone_token="$(pair_device 'Release check phone')"
tablet_token="$(pair_device 'Release check tablet')"
device_list="$(curl --silent --show-error --fail http://127.0.0.1:19754/api/v1/devices \
  --header "Authorization: Bearer $phone_token")"
if [[ "$device_list" != *'Release check phone'* || "$device_list" != *'Release check tablet'* ]]; then
  echo "Production device listing did not return both paired synthetic devices." >&2
  exit 1
fi

unauthorized_status="$(curl --silent --output /dev/null --write-out '%{http_code}' http://127.0.0.1:19754/api/v1/devices)"
if [[ "$unauthorized_status" != 401 ]]; then
  echo "Unauthenticated device listing returned HTTP $unauthorized_status (expected 401)." >&2
  exit 1
fi

"${compose[@]}" exec -T api node --input-type=module -e \
  "import { chmodSync, writeFileSync } from 'node:fs'; const path = '/data/actual-api/release-check.txt'; writeFileSync(path, 'before backup'); chmodSync(path, 0o600);"
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/backup.sh" "$scratch_root/tandem-backup"
wait_for_api
third_token="$(pair_device 'Post-backup tablet')"
"${compose[@]}" exec -T api node --input-type=module -e \
  "import { writeFileSync } from 'node:fs'; writeFileSync('/data/actual-api/release-check.txt', 'after backup');"
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/restore.sh" "$scratch_root/tandem-backup" "$test_epoch-after-restore"
"${compose[@]}" up --detach api
wait_for_api
device_list="$(curl --silent --show-error --fail http://127.0.0.1:19754/api/v1/devices \
  --header "Authorization: Bearer $phone_token")"
if [[ "$device_list" != *'Release check phone'* || "$device_list" != *'Release check tablet'* || "$device_list" == *'Post-backup tablet'* ]]; then
  echo "Production restore did not reinstate the backup's device state." >&2
  exit 1
fi
workspace_value="$("${compose[@]}" exec -T api node --input-type=module -e \
  "import { readFileSync, writeFileSync } from 'node:fs'; const path = '/data/actual-api/release-check.txt'; process.stdout.write(readFileSync(path, 'utf8')); writeFileSync(path, 'node writable after restore');")"
if [[ "$workspace_value" != "before backup" ]]; then
  echo "Production restore did not reinstate the backup's Actual workspace." >&2
  exit 1
fi
current_workspace="$("${compose[@]}" exec -T api node --input-type=module -e \
  "import { readFileSync } from 'node:fs'; process.stdout.write(readFileSync('/data/actual-api/release-check.txt', 'utf8'));")"
if [[ "$current_workspace" != "node writable after restore" ]]; then
  echo "Production restore did not leave the restored workspace writable." >&2
  exit 1
fi
restored_token_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --header "Authorization: Bearer $third_token" http://127.0.0.1:19754/api/v1/devices)"
if [[ "$restored_token_status" != 401 ]]; then
  echo "A device created after the backup remained authorized after restore (HTTP $restored_token_status)." >&2
  exit 1
fi

restored_api_id="$("${compose[@]}" ps --status running -q api)"
if [[ -z "$restored_api_id" ]]; then
  echo "The API is not running after the successful restore." >&2
  exit 1
fi
assert_corrupt_restore_is_rejected() {
  local kind="$1"
  local corrupt_prefix="$scratch_root/corrupt-restore-$kind"
  cp "${scratch_root}/tandem-backup.postgres.dump" "${corrupt_prefix}.postgres.dump"
  cp "${scratch_root}/tandem-backup.actual-workspace.tar.gz" "${corrupt_prefix}.actual-workspace.tar.gz"
  if [[ "$kind" == postgres ]]; then
    truncate -s 64 "${corrupt_prefix}.postgres.dump"
  else
    truncate -s 64 "${corrupt_prefix}.actual-workspace.tar.gz"
  fi

  if TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
    "$project_root/deploy/restore.sh" "$corrupt_prefix" "$test_epoch-corrupt-$kind"; then
    echo "Production restore accepted a truncated $kind backup." >&2
    exit 1
  fi

  local running_api_id devices workspace
  running_api_id="$("${compose[@]}" ps --status running -q api)"
  if [[ "$running_api_id" != "$restored_api_id" ]]; then
    echo "A rejected $kind backup stopped or replaced the running API." >&2
    exit 1
  fi
  devices="$(curl --silent --show-error --fail http://127.0.0.1:19754/api/v1/devices \
    --header "Authorization: Bearer $phone_token")"
  if [[ "$devices" != *'Release check phone'* || "$devices" != *'Release check tablet'* || "$devices" == *'Post-backup tablet'* ]]; then
    echo "A rejected $kind backup changed the live PostgreSQL device records." >&2
    exit 1
  fi
  workspace="$("${compose[@]}" exec -T api node --input-type=module -e \
    "import { readFileSync } from 'node:fs'; process.stdout.write(readFileSync('/data/actual-api/release-check.txt', 'utf8'));")"
  if [[ "$workspace" != "node writable after restore" ]]; then
    echo "A rejected $kind backup changed the live Actual workspace." >&2
    exit 1
  fi
  rm -f "${corrupt_prefix}.postgres.dump" "${corrupt_prefix}.actual-workspace.tar.gz"
}

assert_corrupt_restore_is_rejected postgres
assert_corrupt_restore_is_rejected workspace

# Simulate an in-container process crash after both credentials are durably stored.
api_id="$("${compose[@]}" ps -q api)"
sleep 11
docker exec --user 0 "$api_id" /bin/sh -c 'kill -KILL 1' >/dev/null 2>&1 || true
wait_for_api

device_list="$(curl --silent --show-error --fail http://127.0.0.1:19754/api/v1/devices \
  --header "Authorization: Bearer $tablet_token")"
if [[ "$device_list" != *'Release check phone'* || "$device_list" != *'Release check tablet'* ]]; then
  echo "Paired credentials did not survive the production API crash and restart." >&2
  exit 1
fi

echo "Production image passed health, pairing, multi-device authorization, unauthenticated denial, paired backup/restore, corrupt-restore safety, and crash-recovery checks."
