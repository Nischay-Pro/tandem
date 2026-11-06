#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${TANDEM_ENV_FILE:-$project_root/deploy/compose/.env}"
compose_project="${TANDEM_COMPOSE_PROJECT:-tandem}"
compose_file="$project_root/deploy/compose/production.yml"

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /secure/path/tandem-backup-prefix" >&2
  exit 2
fi
if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file; copy deploy/compose/.env.example and configure it first." >&2
  exit 2
fi

backup_prefix="$1"
backup_dir="$(dirname "$backup_prefix")"
mkdir -p "$backup_dir"
umask 077
postgres_tmp="${backup_prefix}.postgres.dump.$$"
workspace_tmp="${backup_prefix}.actual-workspace.tar.gz.$$"

compose=(docker compose -p "$compose_project" --project-directory "$project_root" --env-file "$env_file" -f "$compose_file")
api_id="$("${compose[@]}" ps -q api)"
if [[ -z "$api_id" ]]; then
  echo "The API container is unavailable; cannot back up its Actual workspace." >&2
  exit 1
fi
api_was_running=false
if [[ -n "$api_id" ]] && "${compose[@]}" ps --status running -q api | grep -q .; then
  api_was_running=true
fi
restart_api() {
  local status=$?
  rm -f "$postgres_tmp" "$workspace_tmp"
  if [[ "$api_was_running" == true ]]; then
    "${compose[@]}" start api >/dev/null || status=$?
  fi
  return "$status"
}
trap restart_api EXIT

if [[ "$api_was_running" == true ]]; then
  "${compose[@]}" stop api
fi
"${compose[@]}" exec -T postgres sh -ec \
  'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  > "$postgres_tmp"
chmod 600 "$postgres_tmp"
docker run --rm --volumes-from "$api_id":ro --entrypoint tar \
  node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 -czf - -C /data/actual-api . \
  > "$workspace_tmp"
chmod 600 "$workspace_tmp"
mv -f "$postgres_tmp" "${backup_prefix}.postgres.dump"
mv -f "$workspace_tmp" "${backup_prefix}.actual-workspace.tar.gz"
echo "Wrote PostgreSQL and Actual workspace backups with mode 600; protect them as financial data."
