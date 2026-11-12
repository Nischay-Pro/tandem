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
backup_gpg_passphrase_file="${TANDEM_BACKUP_GPG_PASSPHRASE_FILE:-}"
if [[ -n "$backup_gpg_passphrase_file" ]]; then
  if [[ ! -f "$backup_gpg_passphrase_file" || ! -r "$backup_gpg_passphrase_file" || ! -s "$backup_gpg_passphrase_file" ]]; then
    echo "TANDEM_BACKUP_GPG_PASSPHRASE_FILE must name a non-empty, readable passphrase file." >&2
    exit 2
  fi
  if ! command -v gpg >/dev/null 2>&1; then
    echo "Install GnuPG on this host to create encrypted backups." >&2
    exit 2
  fi
  if [[ -e "${backup_prefix}.postgres.dump" || -e "${backup_prefix}.actual-workspace.tar.gz" ]]; then
    echo "Plaintext backups already exist for this prefix; choose a new prefix or move them first." >&2
    exit 2
  fi
  postgres_tmp="${backup_prefix}.postgres.dump.gpg.$$"
  workspace_tmp="${backup_prefix}.actual-workspace.tar.gz.gpg.$$"
  postgres_output="${backup_prefix}.postgres.dump.gpg"
  workspace_output="${backup_prefix}.actual-workspace.tar.gz.gpg"
else
  if [[ -e "${backup_prefix}.postgres.dump.gpg" || -e "${backup_prefix}.actual-workspace.tar.gz.gpg" ]]; then
    echo "Encrypted backups already exist for this prefix; choose a new prefix or move them first." >&2
    exit 2
  fi
  postgres_tmp="${backup_prefix}.postgres.dump.$$"
  workspace_tmp="${backup_prefix}.actual-workspace.tar.gz.$$"
  postgres_output="${backup_prefix}.postgres.dump"
  workspace_output="${backup_prefix}.actual-workspace.tar.gz"
fi
last_success_file="${backup_dir}/tandem-backup.last-success"
last_success_tmp="${last_success_file}.tmp.$$"

compose=(docker compose -p "$compose_project" --project-directory "$project_root" --env-file "$env_file" -f "$compose_file")
api_id="$("${compose[@]}" ps --all -q api)"
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
  rm -f "$postgres_tmp" "$workspace_tmp" "$last_success_tmp"
  if [[ "$api_was_running" == true ]]; then
    "${compose[@]}" start api >/dev/null || status=$?
  fi
  if (( status == 0 )); then
    if ! {
      printf '%s\n%s\n' "$(date -u +'%Y-%m-%dT%H:%M:%SZ')" "$(basename "$backup_prefix")" \
        > "$last_success_tmp" &&
        chmod 600 "$last_success_tmp" &&
        mv -f "$last_success_tmp" "$last_success_file"
    }; then
      echo "The backup files were written, but the last-success marker could not be updated." >&2
      status=1
    else
      echo "Last successful backup recorded in $last_success_file"
    fi
  fi
  return "$status"
}
trap restart_api EXIT

if [[ "$api_was_running" == true ]]; then
  "${compose[@]}" stop api
fi
if [[ -n "$backup_gpg_passphrase_file" ]]; then
  "${compose[@]}" exec -T postgres sh -ec \
    'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    | gpg --batch --yes --pinentry-mode loopback --passphrase-file "$backup_gpg_passphrase_file" \
      --symmetric --cipher-algo AES256 --output "$postgres_tmp"
else
  "${compose[@]}" exec -T postgres sh -ec \
    'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
    > "$postgres_tmp"
fi
chmod 600 "$postgres_tmp"
if [[ -n "$backup_gpg_passphrase_file" ]]; then
  docker run --rm --volumes-from "$api_id":ro --entrypoint tar \
    node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 -czf - -C /data/actual-api . \
    | gpg --batch --yes --pinentry-mode loopback --passphrase-file "$backup_gpg_passphrase_file" \
      --symmetric --cipher-algo AES256 --output "$workspace_tmp"
else
  docker run --rm --volumes-from "$api_id":ro --entrypoint tar \
    node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 -czf - -C /data/actual-api . \
    > "$workspace_tmp"
fi
chmod 600 "$workspace_tmp"
mv -f "$postgres_tmp" "$postgres_output"
mv -f "$workspace_tmp" "$workspace_output"
if [[ -n "$backup_gpg_passphrase_file" ]]; then
  echo "Wrote encrypted PostgreSQL and Actual workspace backups with mode 600. Keep the passphrase separate from the backups."
else
  echo "Wrote plaintext PostgreSQL and Actual workspace backups with mode 600; protect them as financial data."
fi
