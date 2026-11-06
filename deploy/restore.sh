#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
env_file="${TANDEM_ENV_FILE:-$project_root/deploy/compose/.env}"
compose_project="${TANDEM_COMPOSE_PROJECT:-tandem}"
compose_file="$project_root/deploy/compose/production.yml"

if [[ $# -ne 2 || ! -f "${1}.postgres.dump" || ! -f "${1}.actual-workspace.tar.gz" ]]; then
  echo "Usage: $0 /secure/path/tandem-backup-prefix NEW_CHANGE_FEED_EPOCH" >&2
  exit 2
fi
if [[ ! -f "$env_file" ]]; then
  echo "Missing $env_file; copy deploy/compose/.env.example and configure it first." >&2
  exit 2
fi

backup_prefix="$(cd "$(dirname "$1")" && pwd)/$(basename "$1")"
new_epoch="$2"
if [[ ! "$new_epoch" =~ ^[A-Za-z0-9._:-]{1,100}$ ]]; then
  echo "NEW_CHANGE_FEED_EPOCH must contain 1 to 100 letters, digits, dots, underscores, colons, or hyphens." >&2
  exit 2
fi
old_epoch="$(sed -n 's/^CHANGE_FEED_EPOCH=//p' "$env_file" | tail -n 1)"
if [[ "$new_epoch" == "$old_epoch" ]]; then
  echo "Choose a new CHANGE_FEED_EPOCH; it must differ from the current value." >&2
  exit 2
fi
chmod 600 "${backup_prefix}.postgres.dump" "${backup_prefix}.actual-workspace.tar.gz"
compose=(docker compose -p "$compose_project" --project-directory "$project_root" --env-file "$env_file" -f "$compose_file")
api_id="$("${compose[@]}" ps -q api)"
if [[ -z "$api_id" ]]; then
  echo "The API container must already exist before restore." >&2
  exit 1
fi
"${compose[@]}" exec -T postgres pg_restore --list < "${backup_prefix}.postgres.dump" >/dev/null
tar -tzf "${backup_prefix}.actual-workspace.tar.gz" >/dev/null
"${compose[@]}" stop api
"${compose[@]}" exec -T postgres sh -ec \
  'pg_restore --exit-on-error --single-transaction --clean --if-exists --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"' \
  < "${backup_prefix}.postgres.dump"
docker run --rm -i --volumes-from "$api_id" --entrypoint sh \
  node:26.13.0-trixie-slim@sha256:5adad64f9a1fca2d51972caa65983634c8a774eb300d72b20f7f2a0f60368072 -ec \
  'mkdir -p /data/actual-api/.restore && find /data/actual-api/.restore -mindepth 1 -maxdepth 1 -exec rm -rf -- {} + && tar -xzf - --no-same-owner -C /data/actual-api/.restore && find /data/actual-api -mindepth 1 -maxdepth 1 ! -name .restore -exec rm -rf -- {} + && find /data/actual-api/.restore -mindepth 1 -maxdepth 1 -exec mv -t /data/actual-api -- {} + && rmdir /data/actual-api/.restore && chown -R node:node /data/actual-api' \
  < "${backup_prefix}.actual-workspace.tar.gz"
env_tmp="$(mktemp "${env_file}.XXXXXX")"
sed "s/^CHANGE_FEED_EPOCH=.*/CHANGE_FEED_EPOCH=$new_epoch/" "$env_file" > "$env_tmp"
chmod 600 "$env_tmp"
mv "$env_tmp" "$env_file"
echo "Database and Actual workspace restored with a new feed epoch. Start and verify the API now:"
echo "  docker compose --project-directory '$project_root' --env-file '$env_file' -f '$compose_file' up -d api"
