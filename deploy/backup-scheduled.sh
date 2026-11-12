#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
backup_dir="${TANDEM_BACKUP_DIRECTORY:-/secure/tandem-backups}"

if [[ $# -ne 0 ]]; then
  echo "Usage: $0" >&2
  exit 2
fi

umask 077
mkdir -p "$backup_dir"
chmod 700 "$backup_dir"
timestamp="$(date -u +'%Y%m%dT%H%M%SZ')"
exec "$project_root/deploy/backup.sh" "$backup_dir/tandem-${timestamp}-$$"
