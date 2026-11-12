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

backup_prefix="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
plain_postgres="${backup_prefix}.postgres.dump"
plain_workspace="${backup_prefix}.actual-workspace.tar.gz"
encrypted_postgres="${plain_postgres}.gpg"
encrypted_workspace="${plain_workspace}.gpg"
has_plain_backup=false
has_encrypted_backup=false
if [[ -f "$plain_postgres" && -f "$plain_workspace" && ! -e "$encrypted_postgres" && ! -e "$encrypted_workspace" ]]; then
  has_plain_backup=true
elif [[ -f "$encrypted_postgres" && -f "$encrypted_workspace" && ! -e "$plain_postgres" && ! -e "$plain_workspace" ]]; then
  has_encrypted_backup=true
else
  echo "Provide exactly one complete plaintext backup pair or one complete .gpg backup pair for this prefix." >&2
  exit 2
fi

postgres_input="$plain_postgres"
workspace_input="$plain_workspace"
postgres_tmp=""
workspace_tmp=""
if [[ "$has_encrypted_backup" == true ]]; then
  backup_gpg_passphrase_file="${TANDEM_BACKUP_GPG_PASSPHRASE_FILE:-}"
  if [[ -z "$backup_gpg_passphrase_file" || ! -f "$backup_gpg_passphrase_file" || ! -r "$backup_gpg_passphrase_file" || ! -s "$backup_gpg_passphrase_file" ]]; then
    echo "Set TANDEM_BACKUP_GPG_PASSPHRASE_FILE to the non-empty, readable passphrase file used for this backup." >&2
    exit 2
  fi
  if ! command -v gpg >/dev/null 2>&1; then
    echo "Install GnuPG on this host to restore encrypted backups." >&2
    exit 2
  fi
  umask 077
  postgres_tmp="$(mktemp)"
  workspace_tmp="$(mktemp)"
  trap 'rm -f "$postgres_tmp" "$workspace_tmp"' EXIT
  gpg --batch --yes --pinentry-mode loopback --passphrase-file "$backup_gpg_passphrase_file" \
    --decrypt --output "$postgres_tmp" "$encrypted_postgres"
  gpg --batch --yes --pinentry-mode loopback --passphrase-file "$backup_gpg_passphrase_file" \
    --decrypt --output "$workspace_tmp" "$encrypted_workspace"
  chmod 600 "$postgres_tmp" "$workspace_tmp"
  postgres_input="$postgres_tmp"
  workspace_input="$workspace_tmp"
elif [[ ! -f "$plain_postgres" || ! -f "$plain_workspace" ]]; then
  echo "The plaintext PostgreSQL dump and Actual workspace archive are both required." >&2
  exit 2
fi

chmod 600 "$postgres_input" "$workspace_input"
compose=(docker compose -p "$compose_project" --project-directory "$project_root" --env-file "$env_file" -f "$compose_file")
api_id="$("${compose[@]}" ps --all -q tandem-api)"
if [[ -z "$api_id" ]]; then
  echo "The API container must already exist before restore." >&2
  exit 1
fi
"${compose[@]}" exec -T tandem-postgres pg_restore --list < "$postgres_input" >/dev/null
tar -tzf "$workspace_input" >/dev/null

safety_prefix="${backup_prefix}.before-restore-$(date -u +%Y%m%dT%H%M%SZ)-$$"
if [[ "$has_encrypted_backup" == true ]]; then
  TANDEM_COMPOSE_PROJECT="$compose_project" TANDEM_ENV_FILE="$env_file" \
    TANDEM_BACKUP_GPG_PASSPHRASE_FILE="$backup_gpg_passphrase_file" \
    "$project_root/deploy/backup.sh" "$safety_prefix"
else
  TANDEM_COMPOSE_PROJECT="$compose_project" TANDEM_ENV_FILE="$env_file" \
    "$project_root/deploy/backup.sh" "$safety_prefix"
fi
echo "Safety backup prefix: $safety_prefix"

"${compose[@]}" stop tandem-api
restore_started=true
restore_completed=false
report_interrupted_restore() {
  local status=$?
  trap - EXIT
  if [[ "$restore_started" == true && "$restore_completed" != true ]]; then
    echo "Restore did not complete. Keep the API stopped until you recover the pre-restore state." >&2
    printf 'To recover the pre-restore data, run: "%s" "%s"\n' \
      "$project_root/deploy/restore.sh" "$safety_prefix" >&2
  fi
  if [[ -n "$postgres_tmp" || -n "$workspace_tmp" ]]; then
    rm -f "$postgres_tmp" "$workspace_tmp"
  fi
  exit "$status"
}
trap report_interrupted_restore EXIT

"${compose[@]}" exec -T tandem-postgres sh -ec \
  'pg_restore --exit-on-error --single-transaction --clean --if-exists --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < "$postgres_input"
docker run --rm -i --volumes-from "$api_id" --entrypoint sh \
  node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 -ec \
  'set -eu
  workspace=/data/actual-api
  restore_id="$(cat /proc/sys/kernel/random/uuid)"
  staged="$workspace/.restore-new-$restore_id"
  previous="$workspace/.restore-previous-$restore_id"
  state=preparing

  entry_count() {
    count=0
    for entry in "$1"/* "$1"/.[!.]* "$1"/..?*; do
      [ -e "$entry" ] || [ -L "$entry" ] || continue
      [ "$entry" = "$staged" ] && continue
      [ "$entry" = "$previous" ] && continue
      count=$((count + 1))
    done
    printf "%s" "$count"
  }

  rollback() {
    status=$?
    trap - EXIT
    if [ "$state" = installing ]; then
      for entry in "$workspace"/* "$workspace"/.[!.]* "$workspace"/..?*; do
        [ -e "$entry" ] || [ -L "$entry" ] || continue
        [ "$entry" = "$staged" ] && continue
        [ "$entry" = "$previous" ] && continue
        rm -rf -- "$entry"
      done
    fi
    if [ "$state" != committed ] && [ -d "$previous" ]; then
      for entry in "$previous"/* "$previous"/.[!.]* "$previous"/..?*; do
        [ -e "$entry" ] || [ -L "$entry" ] || continue
        mv -- "$entry" "$workspace/"
      done
    fi
    rm -rf -- "$staged"
    exit "$status"
  }
  trap rollback EXIT

  mkdir -m 700 "$staged"
  tar -xzf - --no-same-owner -C "$staged"
  expected_count="$(entry_count "$staged")"
  [ "$expected_count" -gt 0 ] || { echo "The restored Actual workspace archive is empty." >&2; exit 1; }
  mkdir -m 700 "$previous"

  state=installing
  for entry in "$workspace"/* "$workspace"/.[!.]* "$workspace"/..?*; do
    [ -e "$entry" ] || [ -L "$entry" ] || continue
    [ "$entry" = "$staged" ] && continue
    [ "$entry" = "$previous" ] && continue
    mv -- "$entry" "$previous/"
  done

  for entry in "$staged"/* "$staged"/.[!.]* "$staged"/..?*; do
    [ -e "$entry" ] || [ -L "$entry" ] || continue
    mv -- "$entry" "$workspace/"
  done
  actual_count="$(entry_count "$workspace")"
  [ "$actual_count" -eq "$expected_count" ] || {
    echo "The restored Actual workspace could not be verified; the old workspace is being put back." >&2
    exit 1
  }

  state=committed
  rm -rf -- "$previous" "$staged"
  chown -R node:node "$workspace"
  trap - EXIT' \
  < "$workspace_input"
restore_completed=true
if [[ -n "$postgres_tmp" || -n "$workspace_tmp" ]]; then
  rm -f "$postgres_tmp" "$workspace_tmp"
fi
trap - EXIT
echo "Database and Actual workspace restored. Start and verify the API now:"
echo "  docker compose --project-directory '$project_root' --env-file '$env_file' -f '$compose_file' up -d tandem-api"
