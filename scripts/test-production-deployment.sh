#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_root="$project_root/.dev-tmp/release-check"
mkdir -p "$scratch_root"
env_file="$(mktemp "$scratch_root/production.XXXXXX.env")"
chmod 600 "$env_file"
secret_dir="$scratch_root/secrets"
mkdir -p "$secret_dir"
chmod 700 "$secret_dir"
project="tandem-release-check"
restore_tmp_dir="$scratch_root/restore-tmp"
edge_network="${project}-edge"
test_api_host_port="${TEST_API_HOST_PORT:-19754}"
compose=(docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml")

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  docker network rm "$edge_network" >/dev/null 2>&1 || true
  rm -f "$env_file"
  rm -rf "$secret_dir"
  rm -f \
    "$scratch_root/tandem-backup.postgres.dump" \
    "$scratch_root/tandem-backup.actual-workspace.tar.gz" \
    "$scratch_root/tandem-backup.last-success" \
    "$scratch_root"/tandem-backup*.postgres.dump \
    "$scratch_root"/tandem-backup*.actual-workspace.tar.gz \
    "$scratch_root"/tandem-backup*.postgres.dump.gpg \
    "$scratch_root"/tandem-backup*.actual-workspace.tar.gz.gpg \
    "$scratch_root/tandem-encrypted-backup.postgres.dump.gpg" \
    "$scratch_root/tandem-encrypted-backup.actual-workspace.tar.gz.gpg" \
    "$scratch_root"/tandem-encrypted-backup*.postgres.dump.gpg \
    "$scratch_root"/tandem-encrypted-backup*.actual-workspace.tar.gz.gpg \
    "$scratch_root/release-check-wrong-backup-passphrase" \
    "$scratch_root/encrypted-restore.log" \
    "$scratch_root"/corrupt-restore-*
  rm -rf "$scratch_root/scheduled-backups" "$scratch_root/stale-backups"
  rm -rf "$scratch_root/docker-fail-workspace-restore"
  rm -f "$scratch_root/interrupted-restore.log"
  rm -f "$restore_tmp_dir"/*
  rmdir "$restore_tmp_dir" >/dev/null 2>&1 || true
}
trap cleanup EXIT

test_secret="release-check-$(date +%s)-synthetic"
test_epoch="release-check-$(date +%s)"
auth_key="$(openssl rand -hex 32)"
backup_passphrase_file="$secret_dir/backup_passphrase"
printf '%s' "$test_secret" > "$secret_dir/postgres_password"
printf 'postgresql://tandem_release_check:%s@postgres:5432/tandem_release_check' \
  "$test_secret" > "$secret_dir/database_url"
printf '%s' "$auth_key" > "$secret_dir/auth_token_encryption_key"
touch "$secret_dir/auth_token_encryption_key_previous"
touch "$secret_dir/actual_session_token"
printf '%s' "release-check-gpg-passphrase-$test_epoch" > "$backup_passphrase_file"
chmod 444 "$secret_dir"/*
chmod 600 "$backup_passphrase_file"
cat > "$env_file" <<EOF
POSTGRES_DB=tandem_release_check
POSTGRES_USER=tandem_release_check
POSTGRES_PASSWORD_FILE=$secret_dir/postgres_password
DATABASE_URL_FILE=$secret_dir/database_url
AUTH_TOKEN_ENCRYPTION_KEY_FILE=$secret_dir/auth_token_encryption_key
AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE=$secret_dir/auth_token_encryption_key_previous
ACTUAL_SESSION_TOKEN_FILE=$secret_dir/actual_session_token
TEST_API_HOST_PORT=$test_api_host_port
TRUST_PROXY=127.0.0.1
TANDEM_EDGE_NETWORK=$edge_network
ACTUAL_SERVER_URL=
ACTUAL_PUBLIC_URL=
EOF

docker network create "$edge_network" >/dev/null
if TRUST_PROXY= "${compose[@]}" config --quiet >/dev/null 2>&1; then
  echo "Production Compose accepted an empty TRUST_PROXY value." >&2
  exit 1
fi
"${compose[@]}" config --quiet
"${compose[@]}" up --build --detach
api_id="$("${compose[@]}" ps -q api)"
api_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$api_id")"
if [[ "$api_environment" == *"$test_secret"* || "$api_environment" == *"$auth_key"* ]]; then
  echo "Production secrets were exposed in the API container environment." >&2
  exit 1
fi
if [[ "$api_environment" != *"DATABASE_URL_FILE=/run/secrets/database_url"* || \
  "$api_environment" != *"ACTUAL_SESSION_TOKEN_FILE=/run/secrets/actual_session_token"* || \
  "$api_environment" != *"AUTH_TOKEN_ENCRYPTION_KEY_FILE=/run/secrets/auth_token_encryption_key"* || \
  "$api_environment" != *"AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE=/run/secrets/auth_token_encryption_key_previous"* ]]; then
  echo "The production API did not receive its secret-file paths." >&2
  exit 1
fi

wait_for_api() {
  local attempts="${1:-60}"
  for _ in $(seq 1 "$attempts"); do
    if curl --silent --fail "http://127.0.0.1:$test_api_host_port/api/v1/health/ready" >/dev/null; then
      return 0
    fi
    sleep 1
  done
  "${compose[@]}" logs --no-color api postgres
  echo "Production stack did not become ready." >&2
  return 1
}
wait_for_api

scheduled_backup_dir="$scratch_root/scheduled-backups"
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  TANDEM_BACKUP_DIRECTORY="$scheduled_backup_dir" \
  "$project_root/deploy/backup-scheduled.sh"
wait_for_api
TANDEM_BACKUP_MAX_AGE_HOURS=26 \
  "$project_root/deploy/backup-status.sh" "$scheduled_backup_dir"
scheduled_marker="$scheduled_backup_dir/tandem-backup.last-success"
scheduled_marker_before_failure="$(cat "$scheduled_marker")"
if TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  TANDEM_BACKUP_DIRECTORY="$scheduled_backup_dir" \
  TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$secret_dir/missing-passphrase" \
  "$project_root/deploy/backup-scheduled.sh" >/dev/null 2>&1; then
  echo "The scheduled backup accepted a missing encryption passphrase file." >&2
  exit 1
fi
if [[ "$(cat "$scheduled_marker")" != "$scheduled_marker_before_failure" ]]; then
  echo "A failed scheduled backup replaced the last-success marker." >&2
  exit 1
fi
"$project_root/deploy/backup-status.sh" "$scheduled_backup_dir"

stale_backup_dir="$scratch_root/stale-backups"
mkdir -m 700 -p "$stale_backup_dir"
scheduled_prefix="$(sed -n '2p' "$scheduled_marker")"
for suffix in .postgres.dump .actual-workspace.tar.gz; do
  cp "$scheduled_backup_dir/$scheduled_prefix$suffix" "$stale_backup_dir/"
done
{
  echo "2000-01-01T00:00:00Z"
  echo "$scheduled_prefix"
} > "$stale_backup_dir/tandem-backup.last-success"
if "$project_root/deploy/backup-status.sh" "$stale_backup_dir" >/dev/null 2>&1; then
  echo "The backup status check accepted an old success marker." >&2
  exit 1
fi

create_test_device() {
  local name="$1"
  local token credential_hash device_id
  token="$(openssl rand -base64 32 | tr '+/' '-_' | tr -d '=\n' | cut -c1-43)"
  credential_hash="$(printf '%s' "$token" | sha256sum | cut -d ' ' -f1)"
  device_id="$(cat /proc/sys/kernel/random/uuid)"
  "${compose[@]}" exec -T postgres psql -U tandem_release_check -d tandem_release_check \
    --set=device_id="$device_id" \
    --set=device_name="$name" \
    --set=credential_hash="$credential_hash" >/dev/null <<'SQL'
INSERT INTO tandem_devices (
  device_id, principal_id, device_name, credential_hash, budget_ids
) VALUES (:'device_id'::uuid, 'release-check', :'device_name', :'credential_hash', ARRAY['*']);
SQL
  printf '%s' "$token"
}

login_status="$(curl --silent --output /dev/null --write-out '%{http_code}' \
  --request POST "http://127.0.0.1:$test_api_host_port/api/v1/auth/login" \
  --header 'Content-Type: application/json' \
  --data '{"password":"unused-test-password","deviceName":"Release check phone"}')"
if [[ "$login_status" != 503 ]]; then
  echo "Sign-in without an Actual URL returned HTTP $login_status (expected 503)." >&2
  exit 1
fi

phone_token="$(create_test_device 'Release check phone')"
tablet_token="$(create_test_device 'Release check tablet')"
device_list="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" \
  --header "Authorization: Bearer $phone_token")"
if [[ "$device_list" != *'Release check phone'* || "$device_list" != *'Release check tablet'* ]]; then
  echo "Production device listing did not return both paired synthetic devices." >&2
  exit 1
fi

unauthorized_status="$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$test_api_host_port/api/v1/devices")"
if [[ "$unauthorized_status" != 401 ]]; then
  echo "Unauthenticated device listing returned HTTP $unauthorized_status (expected 401)." >&2
  exit 1
fi

"${compose[@]}" exec -T api node --input-type=module -e \
  "import { chmodSync, writeFileSync } from 'node:fs'; const path = '/data/actual-api/release-check.txt'; writeFileSync(path, 'before backup'); chmodSync(path, 0o600);"
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/backup.sh" "$scratch_root/tandem-backup"
wait_for_api
third_token="$(create_test_device 'Post-backup tablet')"
"${compose[@]}" exec -T api node --input-type=module -e \
  "import { writeFileSync } from 'node:fs'; writeFileSync('/data/actual-api/release-check.txt', 'after backup');"
real_docker="$(command -v docker)"
docker_shim_dir="$scratch_root/docker-fail-workspace-restore"
mkdir -p "$docker_shim_dir"
cat > "$docker_shim_dir/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
previous_arg=""
for argument in "$@"; do
  if [[ "$previous_arg" == "--volumes-from" && "$argument" != *":ro" ]]; then
    echo "Injected failure before installing the restored Actual workspace." >&2
    exit 97
  fi
  previous_arg="$argument"
done
exec "$REAL_DOCKER" "$@"
SH
chmod 700 "$docker_shim_dir/docker"
interrupted_restore_log="$scratch_root/interrupted-restore.log"
if REAL_DOCKER="$real_docker" PATH="$docker_shim_dir:$PATH" \
  TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/restore.sh" "$scratch_root/tandem-backup" \
  >"$interrupted_restore_log" 2>&1; then
  echo "The injected interruption unexpectedly completed the restore." >&2
  exit 1
fi
if ! grep -Fq "Injected failure before installing the restored Actual workspace." "$interrupted_restore_log"; then
  cat "$interrupted_restore_log" >&2
  echo "The restore failed before reaching the injected interruption point." >&2
  exit 1
fi
safety_prefix="$(sed -n 's/^Safety backup prefix: //p' "$interrupted_restore_log" | tail -n 1)"
if [[ -z "$safety_prefix" || ! -f "${safety_prefix}.postgres.dump" || \
  ! -f "${safety_prefix}.actual-workspace.tar.gz" ]]; then
  echo "An interrupted restore did not leave its complete safety backup." >&2
  exit 1
fi
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/restore.sh" "$safety_prefix"
"${compose[@]}" up --detach api
wait_for_api
recovered_devices="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" \
  --header "Authorization: Bearer $phone_token")"
if [[ "$recovered_devices" != *'Release check phone'* || \
  "$recovered_devices" != *'Release check tablet'* || \
  "$recovered_devices" != *'Post-backup tablet'* ]]; then
  echo "Restoring the safety copy did not recover the pre-restore PostgreSQL state." >&2
  exit 1
fi
recovered_workspace="$("${compose[@]}" exec -T api node --input-type=module -e \
  "import { readFileSync } from 'node:fs'; process.stdout.write(readFileSync('/data/actual-api/release-check.txt', 'utf8'));")"
if [[ "$recovered_workspace" != "after backup" ]]; then
  echo "Restoring the safety copy did not recover the pre-restore Actual workspace." >&2
  exit 1
fi

TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
  "$project_root/deploy/restore.sh" "$scratch_root/tandem-backup"
"${compose[@]}" up --detach api
wait_for_api
device_list="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" \
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
  --header "Authorization: Bearer $third_token" "http://127.0.0.1:$test_api_host_port/api/v1/devices")"
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
  local truncation="$2"
  local corrupt_prefix="$scratch_root/corrupt-restore-$kind-$truncation"
  local restore_log="${corrupt_prefix}.restore.log"
  local safety_prefix
  cp "${scratch_root}/tandem-backup.postgres.dump" "${corrupt_prefix}.postgres.dump"
  cp "${scratch_root}/tandem-backup.actual-workspace.tar.gz" "${corrupt_prefix}.actual-workspace.tar.gz"
  if [[ "$kind" == postgres ]]; then
    if [[ "$truncation" == mid-file ]]; then
      local dump_size truncated_size low high candidate_size checked_candidates candidate_dump
      dump_size="$(wc -c < "${corrupt_prefix}.postgres.dump")"
      candidate_dump="${corrupt_prefix}.candidate.dump"
      low=1
      high=$((dump_size - 1))
      truncated_size=0
      checked_candidates=0
      while (( low <= high )); do
        candidate_size=$(((low + high) / 2))
        checked_candidates=$((checked_candidates + 1))
        head -c "$candidate_size" "${scratch_root}/tandem-backup.postgres.dump" > "$candidate_dump"
        if "${compose[@]}" exec -T postgres pg_restore --list \
          < "$candidate_dump" >/dev/null 2>&1; then
          truncated_size="$candidate_size"
          high=$((candidate_size - 1))
        else
          low=$((candidate_size + 1))
        fi
      done
      if (( truncated_size <= 64 || truncated_size >= dump_size )); then
        rm -f "$candidate_dump"
        echo "Could not find a truncated PostgreSQL archive that passes the list preflight after checking $checked_candidates prefixes (dump size $dump_size)." >&2
        exit 1
      fi
      head -c "$truncated_size" "${scratch_root}/tandem-backup.postgres.dump" > "${corrupt_prefix}.postgres.dump"
      rm -f "$candidate_dump"
      if ! "${compose[@]}" exec -T postgres pg_restore --list \
        < "${corrupt_prefix}.postgres.dump" >/dev/null 2>&1; then
        echo "The PostgreSQL archive cutoff did not survive the list preflight." >&2
        exit 1
      fi
      echo "Testing transactional restore with a ${truncated_size}-byte PostgreSQL archive cutoff."
    else
      truncate -s 64 "${corrupt_prefix}.postgres.dump"
    fi
  else
    truncate -s 64 "${corrupt_prefix}.actual-workspace.tar.gz"
  fi

  if TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" \
    "$project_root/deploy/restore.sh" "$corrupt_prefix" \
    >"$restore_log" 2>&1; then
    echo "Production restore accepted a truncated $kind backup ($truncation)." >&2
    exit 1
  fi

  if [[ "$kind" == postgres && "$truncation" == mid-file ]]; then
    safety_prefix="$(sed -n 's/^Safety backup prefix: //p' "$restore_log" | tail -n 1)"
    if ! grep -Fq "Restore did not complete. Keep the API stopped" "$restore_log" || \
      ! grep -Fq "To recover the pre-restore data, run:" "$restore_log" || \
      [[ -z "$safety_prefix" || ! -f "${safety_prefix}.postgres.dump" || \
        ! -f "${safety_prefix}.actual-workspace.tar.gz" ]]; then
      cat "$restore_log" >&2
      echo "A failed transactional restore did not explain how to recover its safety copy." >&2
      exit 1
    fi
  fi

  local running_api_id devices workspace
  if [[ "$kind" == postgres && "$truncation" == mid-file ]]; then
    local api_state
    api_state="$(docker inspect --format '{{.State.Status}}' "$restored_api_id")"
    if [[ "$api_state" != exited ]]; then
      echo "The mid-file corruption check did not reach PostgreSQL restore after API shutdown." >&2
      exit 1
    fi
    "${compose[@]}" up --detach api
    wait_for_api 15
  fi
  running_api_id="$("${compose[@]}" ps --status running -q api)"
  if [[ "$running_api_id" != "$restored_api_id" ]]; then
    echo "A rejected $kind backup stopped or replaced the running API ($truncation)." >&2
    exit 1
  fi
  devices="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" \
    --header "Authorization: Bearer $phone_token")"
  if [[ "$devices" != *'Release check phone'* || "$devices" != *'Release check tablet'* || "$devices" == *'Post-backup tablet'* ]]; then
    echo "A rejected $kind backup changed the live PostgreSQL device records ($truncation)." >&2
    exit 1
  fi
  workspace="$("${compose[@]}" exec -T api node --input-type=module -e \
    "import { readFileSync } from 'node:fs'; process.stdout.write(readFileSync('/data/actual-api/release-check.txt', 'utf8'));")"
  if [[ "$workspace" != "node writable after restore" ]]; then
    echo "A rejected $kind backup changed the live Actual workspace ($truncation)." >&2
    exit 1
  fi
  rm -f "${corrupt_prefix}.postgres.dump" "${corrupt_prefix}.actual-workspace.tar.gz"
}

assert_corrupt_restore_is_rejected postgres header
assert_corrupt_restore_is_rejected postgres mid-file
assert_corrupt_restore_is_rejected workspace header

docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml" exec -T api node --input-type=module -e "import { writeFileSync } from 'node:fs'; writeFileSync('/data/actual-api/release-check.txt', 'before encrypted backup');"
TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$backup_passphrase_file" "$project_root/deploy/backup.sh" "$scratch_root/tandem-encrypted-backup"
wait_for_api
if [[ ! -f "$scratch_root/tandem-encrypted-backup.postgres.dump.gpg" || ! -f "$scratch_root/tandem-encrypted-backup.actual-workspace.tar.gz.gpg" || -e "$scratch_root/tandem-encrypted-backup.postgres.dump" || -e "$scratch_root/tandem-encrypted-backup.actual-workspace.tar.gz" ]]; then
  echo "Encrypted backup did not produce exactly the expected ciphertext files." >&2
  exit 1
fi
wrong_passphrase_file="$scratch_root/release-check-wrong-backup-passphrase"
mkdir -m 700 -p "$restore_tmp_dir"
printf '%s' 'incorrect synthetic passphrase' > "$wrong_passphrase_file"
chmod 600 "$wrong_passphrase_file"
if TMPDIR="$restore_tmp_dir" TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$wrong_passphrase_file" "$project_root/deploy/restore.sh" "$scratch_root/tandem-encrypted-backup" >/dev/null 2>&1; then
  echo "Encrypted restore accepted an incorrect passphrase." >&2
  exit 1
fi
if [[ -n "$(find "$restore_tmp_dir" -mindepth 1 -maxdepth 1 -type f -print -quit)" ]]; then
  echo "A rejected encrypted restore left decrypted temporary files." >&2
  exit 1
fi
wait_for_api 15
encrypted_post_backup_token="$(create_test_device 'Post-encrypted-backup tablet')"
docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml" exec -T api node --input-type=module -e "import { writeFileSync } from 'node:fs'; writeFileSync('/data/actual-api/release-check.txt', 'after encrypted backup');"
if ! TMPDIR="$restore_tmp_dir" TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$backup_passphrase_file" \
  "$project_root/deploy/restore.sh" "$scratch_root/tandem-encrypted-backup" \
  >"$scratch_root/encrypted-restore.log" 2>&1; then
  cat "$scratch_root/encrypted-restore.log" >&2
  exit 1
fi
cat "$scratch_root/encrypted-restore.log"
encrypted_safety_prefix="$(sed -n 's/^Safety backup prefix: //p' "$scratch_root/encrypted-restore.log" | tail -n 1)"
if [[ -z "$encrypted_safety_prefix" || \
  ! -f "${encrypted_safety_prefix}.postgres.dump.gpg" || \
  ! -f "${encrypted_safety_prefix}.actual-workspace.tar.gz.gpg" || \
  -e "${encrypted_safety_prefix}.postgres.dump" || \
  -e "${encrypted_safety_prefix}.actual-workspace.tar.gz" ]]; then
  echo "Encrypted restore did not leave a paired, encrypted safety backup." >&2
  exit 1
fi
if [[ -n "$(find "$restore_tmp_dir" -mindepth 1 -maxdepth 1 -type f -print -quit)" ]]; then
  echo "A successful encrypted restore left decrypted temporary files." >&2
  exit 1
fi
docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml" up --detach api
wait_for_api
encrypted_device_list="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" --header "Authorization: Bearer $phone_token")"
if [[ "$encrypted_device_list" != *'Release check phone'* || "$encrypted_device_list" != *'Release check tablet'* || "$encrypted_device_list" == *'Post-encrypted-backup tablet'* ]]; then
  echo "Encrypted production restore did not reinstate its PostgreSQL backup." >&2
  exit 1
fi
encrypted_workspace_value="$(docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/compose/production.yml" -f "$project_root/deploy/compose/production-check.yml" exec -T api node --input-type=module -e "import { readFileSync } from 'node:fs'; process.stdout.write(readFileSync('/data/actual-api/release-check.txt', 'utf8'));")"
if [[ "$encrypted_workspace_value" != "before encrypted backup" ]]; then
  echo "Encrypted production restore did not reinstate its Actual workspace." >&2
  exit 1
fi
encrypted_token_status="$(curl --silent --output /dev/null --write-out '%{http_code}' --header "Authorization: Bearer $encrypted_post_backup_token" "http://127.0.0.1:$test_api_host_port/api/v1/devices")"
if [[ "$encrypted_token_status" != 401 ]]; then
  echo "Encrypted restore kept a device created after the backup (HTTP $encrypted_token_status)." >&2
  exit 1
fi

# Simulate an in-container process crash after both device records are stored.
api_id="$("${compose[@]}" ps -q api)"
sleep 11
docker exec --user 0 "$api_id" /bin/sh -c 'kill -KILL 1' >/dev/null 2>&1 || true
wait_for_api

device_list="$(curl --silent --show-error --fail "http://127.0.0.1:$test_api_host_port/api/v1/devices" \
  --header "Authorization: Bearer $tablet_token")"
if [[ "$device_list" != *'Release check phone'* || "$device_list" != *'Release check tablet'* ]]; then
  echo "Paired credentials did not survive the production API crash and restart." >&2
  exit 1
fi

echo "Production image passed health, sign-in routing, authorization, plaintext and encrypted backup/restore, wrong-passphrase and corrupt-restore safety, and crash-recovery checks."
