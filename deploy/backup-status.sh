#!/usr/bin/env bash
set -euo pipefail

backup_dir="${1:-${TANDEM_BACKUP_DIRECTORY:-/secure/tandem-backups}}"
max_age_hours="${TANDEM_BACKUP_MAX_AGE_HOURS:-26}"
last_success_file="$backup_dir/tandem-backup.last-success"

if [[ $# -gt 1 ]]; then
  echo "Usage: $0 [backup-directory]" >&2
  exit 2
fi
if [[ ! "$max_age_hours" =~ ^[1-9][0-9]*$ ]]; then
  echo "TANDEM_BACKUP_MAX_AGE_HOURS must be a positive whole number." >&2
  exit 2
fi
if [[ ! -s "$last_success_file" ]]; then
  echo "No successful Tandem backup is recorded in $backup_dir." >&2
  exit 1
fi

mapfile -t marker_values < "$last_success_file"
if [[ ${#marker_values[@]} -ne 2 || -z "${marker_values[0]}" || -z "${marker_values[1]}" ||
  "${marker_values[1]}" == */* ]]; then
  echo "The Tandem backup success marker is invalid." >&2
  exit 1
fi

timestamp="${marker_values[0]}"
prefix="${marker_values[1]}"
if ! backup_epoch="$(date -u -d "$timestamp" +%s 2>/dev/null)"; then
  echo "The Tandem backup success marker has an invalid timestamp." >&2
  exit 1
fi
now_epoch="$(date -u +%s)"
age_seconds=$((now_epoch - backup_epoch))
if (( age_seconds < 0 || age_seconds > max_age_hours * 3600 )); then
  echo "The last Tandem backup is older than ${max_age_hours} hours ($timestamp)." >&2
  exit 1
fi

plain_postgres="$backup_dir/$prefix.postgres.dump"
plain_workspace="$backup_dir/$prefix.actual-workspace.tar.gz"
encrypted_postgres="$backup_dir/$prefix.postgres.dump.gpg"
encrypted_workspace="$backup_dir/$prefix.actual-workspace.tar.gz.gpg"
if [[ -s "$plain_postgres" && -s "$plain_workspace" &&
  ! -e "$encrypted_postgres" && ! -e "$encrypted_workspace" ]]; then
  backup_kind="plaintext"
elif [[ -s "$encrypted_postgres" && -s "$encrypted_workspace" &&
  ! -e "$plain_postgres" && ! -e "$plain_workspace" ]]; then
  backup_kind="encrypted"
else
  echo "The last-success marker does not have a complete, non-empty backup pair." >&2
  exit 1
fi

printf 'OK: last %s Tandem backup completed at %s (%s).\n' \
  "$backup_kind" "$timestamp" "$prefix"
