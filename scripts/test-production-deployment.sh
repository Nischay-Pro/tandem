#!/usr/bin/env bash
# Deploys the production Compose stack locally and checks readiness, backups, encrypted backups and restores.
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
scratch_root="${TANDEM_RELEASE_CHECK_DIRECTORY:-$project_root/.dev-tmp/release-check}"
mkdir -p "$scratch_root"
env_file="$(mktemp "$scratch_root/production.XXXXXX.env")"
chmod 600 "$env_file"
secret_dir="$scratch_root/secrets"
mkdir -m 700 -p "$secret_dir"
project="tandem-release-check"
edge_network="${project}-edge"
test_api_host_port="${TEST_API_HOST_PORT:-$(python3 -c 'import socket; s = socket.socket(); s.bind(("127.0.0.1", 0)); print(s.getsockname()[1]); s.close()')}"
compose=(docker compose -p "$project" --project-directory "$project_root" --env-file "$env_file" -f "$project_root/deploy/docker-compose.yml" -f "$project_root/scripts/compose/production-check.yml")
data=(env TANDEM_COMPOSE_PROJECT="$project" TANDEM_ENV_FILE="$env_file" "$project_root/deploy/tandem-data.py")

cleanup() {
  "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  docker network rm "$edge_network" >/dev/null 2>&1 || true
  rm -rf "$env_file" "$secret_dir" "$scratch_root/scheduled-backups" "$scratch_root/stale-backups"
  rm -f "$scratch_root"/tandem-backup* "$scratch_root"/tandem-encrypted-backup* "$scratch_root"/corrupt-restore-* \
    "$scratch_root"/tandem-full-destination*
}
trap cleanup EXIT

test_secret="release-check-$(date +%s)-synthetic"
passphrase_file="$secret_dir/backup_passphrase"
printf '%s' "release-check-gpg-passphrase-$(date +%s)" > "$passphrase_file"
chmod 600 "$passphrase_file"
cat > "$env_file" <<ENV
TANDEM_API_TAG=${TANDEM_API_TAG:-release-check}
TANDEM_API_HOST_PORT=$test_api_host_port
TANDEM_EDGE_NETWORK=$edge_network
POSTGRES_PASSWORD=$test_secret
TRUST_PROXY=
ACTUAL_SERVER_URL=http://actual.invalid:5006
ENV
docker network create "$edge_network" >/dev/null

"${compose[@]}" config --quiet
"${compose[@]}" config --format json > "$scratch_root/production-compose.json"
jq -e --arg port "$test_api_host_port" \
  '.services["tandem-api"].ports | any(.host_ip == "127.0.0.1" and .published == $port and .target == 19753)' \
  "$scratch_root/production-compose.json" >/dev/null
if [[ "${TANDEM_API_IMAGE_PREBUILT:-false}" == "true" ]]; then
  "${compose[@]}" pull tandem-postgres
  "${compose[@]}" up --pull never --no-build --detach
else
  "${compose[@]}" up --build --detach
fi
api_environment="$(docker inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$("${compose[@]}" ps -q tandem-api)")"
if [[ "$api_environment" != *"DATABASE_URL=postgresql://tandem:$test_secret@tandem-postgres:5432/tandem"* || \
  "$api_environment" != *"ACTUAL_SERVER_URL=http://actual.invalid:5006"* ]]; then
  echo "The extension service did not receive its database and Actual server settings." >&2
  exit 1
fi

wait_for_api() {
  for _ in $(seq 1 "${1:-60}"); do
    curl --silent --fail "http://127.0.0.1:$test_api_host_port/extension/v1/health/ready" >/dev/null && return 0
    sleep 1
  done
  "${compose[@]}" logs --no-color tandem-api tandem-postgres
  echo "Production stack did not become ready." >&2
  return 1
}
wait_for_api

sql() {
  "${compose[@]}" exec -T tandem-postgres psql --quiet --tuples-only --no-align -U tandem -d tandem -c "$1"
}
put_record() {
  sql "INSERT INTO planning_records (scope, record_id, record_type, schema_version, revision, deleted, iv, ciphertext, seq)
       VALUES ('release|check', '$1', 'planning.savings_goal', 1, 1, false, 'aXY=', 'c2VhbGVk', nextval('planning_record_seq'))" >/dev/null
}
records() {
  sql "SELECT string_agg(record_id, ',' ORDER BY record_id) FROM planning_records WHERE scope = 'release|check'"
}

unsigned="$(curl --silent --output /dev/null --write-out '%{http_code}' "http://127.0.0.1:$test_api_host_port/extension/v1/budgets/b/changes")"
[[ "$unsigned" == 401 ]] || { echo "An unsigned request returned HTTP $unsigned (expected 401)." >&2; exit 1; }
unreachable="$(curl --silent --output /dev/null --write-out '%{http_code}' --header 'x-actual-token: t' "http://127.0.0.1:$test_api_host_port/extension/v1/budgets/b/changes")"
[[ "$unreachable" == 503 ]] || { echo "An unreachable Actual server returned HTTP $unreachable (expected 503)." >&2; exit 1; }

put_record before-backup

scheduled_dir="$scratch_root/scheduled-backups"
TANDEM_BACKUP_DIRECTORY="$scheduled_dir" "${data[@]}" scheduled-backup
TANDEM_BACKUP_MAX_AGE_HOURS=26 "${data[@]}" status "$scheduled_dir"
marker="$scheduled_dir/tandem-backup.last-success"
marker_before="$(cat "$marker")"
if TANDEM_BACKUP_DIRECTORY="$scheduled_dir" TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$secret_dir/missing" \
  "${data[@]}" scheduled-backup >/dev/null 2>&1; then
  echo "A scheduled backup accepted a missing passphrase file." >&2
  exit 1
fi
[[ "$(cat "$marker")" == "$marker_before" ]] || { echo "A failed backup replaced the last-success marker." >&2; exit 1; }

if ( ulimit -f 1; "${data[@]}" backup "$scratch_root/tandem-full-destination" ) >/dev/null 2>&1; then
  echo "A backup succeeded after its destination stopped accepting writes." >&2
  exit 1
fi
if compgen -G "$scratch_root/tandem-full-destination*" >/dev/null; then
  echo "A failed backup left a partial file behind." >&2
  exit 1
fi

stale_dir="$scratch_root/stale-backups"
mkdir -m 700 -p "$stale_dir"
cp "$scheduled_dir/$(sed -n '2p' "$marker").postgres.dump" "$stale_dir/"
printf '2000-01-01T00:00:00Z\n%s\n' "$(sed -n '2p' "$marker")" > "$stale_dir/tandem-backup.last-success"
if "${data[@]}" status "$stale_dir" >/dev/null 2>&1; then
  echo "Status accepted a stale backup." >&2
  exit 1
fi

"${data[@]}" backup "$scratch_root/tandem-backup"
put_record after-backup
"${data[@]}" restore "$scratch_root/tandem-backup" > "$scratch_root/tandem-backup-restore.log"
wait_for_api
[[ "$(records)" == "before-backup" ]] || { echo "Restore did not return to the backed-up records." >&2; exit 1; }
safety_prefix="$(sed -n 's/^Safety backup prefix: //p' "$scratch_root/tandem-backup-restore.log")"
"${data[@]}" restore "$safety_prefix" >/dev/null
wait_for_api
[[ "$(records)" == "after-backup,before-backup" ]] || { echo "The safety backup did not hold the pre-restore records." >&2; exit 1; }

TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$passphrase_file" "${data[@]}" backup "$scratch_root/tandem-encrypted-backup"
if [[ -e "$scratch_root/tandem-encrypted-backup.postgres.dump" ]] || \
  grep -q "planning_records" "$scratch_root/tandem-encrypted-backup.postgres.dump.gpg"; then
  echo "The encrypted backup left readable data behind." >&2
  exit 1
fi
put_record after-encrypted-backup
printf 'wrong' > "$secret_dir/wrong"
chmod 600 "$secret_dir/wrong"
if TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$secret_dir/wrong" "${data[@]}" restore "$scratch_root/tandem-encrypted-backup" >/dev/null 2>&1; then
  echo "Restore accepted the wrong backup passphrase." >&2
  exit 1
fi
[[ "$(records)" == "after-backup,after-encrypted-backup,before-backup" ]] || { echo "A rejected restore changed the data." >&2; exit 1; }
TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$passphrase_file" "${data[@]}" restore "$scratch_root/tandem-encrypted-backup" >/dev/null
wait_for_api
[[ "$(records)" == "after-backup,before-backup" ]] || { echo "The encrypted restore did not return to its backup." >&2; exit 1; }

printf 'not a dump' > "$scratch_root/corrupt-restore-1.postgres.dump"
chmod 600 "$scratch_root/corrupt-restore-1.postgres.dump"
if "${data[@]}" restore "$scratch_root/corrupt-restore-1" >/dev/null 2>&1; then
  echo "Restore accepted a corrupt backup." >&2
  exit 1
fi
[[ "$(records)" == "after-backup,before-backup" ]] || { echo "A corrupt restore changed the data." >&2; exit 1; }

echo "Production deployment passed readiness, authorization, backup, encrypted backup and restore checks."
