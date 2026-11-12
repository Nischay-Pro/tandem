#!/usr/bin/env bash
set -euo pipefail

readonly deploy_base_url="${TANDEM_DEPLOY_BASE_URL:-https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy}"
readonly nightly_release_api_url="${TANDEM_NIGHTLY_RELEASE_API_URL:-https://api.github.com/repos/Nischay-Pro/tandem/releases/tags/nightly}"
install_directory="${TANDEM_INSTALL_DIRECTORY:-$PWD/tandem}"
install_directory="$(mkdir -p "$install_directory" && cd "$install_directory" && pwd)"
deployment_directory="$install_directory/deploy"
compose_directory="$deployment_directory/compose"
secrets_directory="$compose_directory/secrets"
environment_file="$compose_directory/.env"
compose_file="$compose_directory/production.yml"
readonly support_files=(
  backup.sh
  backup-scheduled.sh
  backup-status.sh
  restore.sh
  systemd/tandem-backup.service
  systemd/tandem-backup.timer
  nginx/tandem-locations.conf.example
  nginx/tandem-upstreams.conf.example
)

for required_command in curl docker jq openssl sha256sum sudo tar; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$required_command" >&2
    exit 1
  fi
done

docker compose version >/dev/null

if [[ -e "$environment_file" ]]; then
  if [[ ! -e "$compose_file" ]]; then
    printf 'Settings exist at %s but %s is missing; restore the Compose file before starting services.\n' \
      "$environment_file" "$compose_file" >&2
    exit 1
  fi
  printf 'Existing %s is unchanged. The setup command never replaces settings or secrets.\n' "$environment_file"
  printf 'Review it, then start with: docker compose --project-directory %q --env-file %q -f %q up -d\n' \
    "$install_directory" "$environment_file" "$compose_file"
  exit 0
fi

mkdir -p "$secrets_directory" "$deployment_directory/nginx" "$deployment_directory/systemd"
chmod 700 "$secrets_directory"

temporary_directory="$(mktemp -d "$install_directory/.setup.XXXXXX")"
cleanup() {
  rm -rf -- "$temporary_directory"
}
trap cleanup EXIT
umask 077

if [[ -e "$compose_file" ]]; then
  printf 'Refusing to replace existing %s. Move it aside before first-time setup.\n' "$compose_file" >&2
  exit 1
fi
for support_file in "${support_files[@]}"; do
  if [[ -e "$deployment_directory/$support_file" ]]; then
    printf 'Refusing to replace existing %s. Move it aside before first-time setup.\n' \
      "$deployment_directory/$support_file" >&2
    exit 1
  fi
done
curl --fail --silent --show-error --location \
  --header 'Accept: application/vnd.github+json' \
  "$nightly_release_api_url" \
  -o "$temporary_directory/nightly-release.json"
release_asset_urls="$(jq --exit-status --raw-output '
  . as $release |
  [$release.assets[] | select(.name | startswith("Tandem-compose-") and endswith(".tar.gz"))] |
  sort_by(.updated_at // .created_at) | last as $bundle |
  [$release.assets[] | select(.name == ($bundle.name + ".sha256"))] as $checksums |
  if ($bundle.browser_download_url | type) != "string" or ($checksums | length) != 1 or ($checksums[0].browser_download_url | type) != "string" then
    error("the newest Compose bundle has no matching checksum")
  else
    [$bundle.browser_download_url, $checksums[0].browser_download_url] | @tsv
  end' "$temporary_directory/nightly-release.json")" || {
  printf 'The nightly release does not include its pinned Compose bundle. Check the latest Tandem nightly release.\n' >&2
  exit 1
}
read -r compose_bundle_url compose_checksum_url <<< "$release_asset_urls"
case "$compose_bundle_url" in
  https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-*.tar.gz) ;;
  *)
    printf 'The nightly release returned an unexpected Compose download address.\n' >&2
    exit 1
    ;;
esac
if [[ "$compose_checksum_url" != "$compose_bundle_url.sha256" ]]; then
  printf 'The nightly release returned a mismatched Compose checksum address.\n' >&2
  exit 1
fi
curl --fail --silent --show-error --location "$compose_bundle_url" -o "$temporary_directory/compose-bundle.tar.gz"
curl --fail --silent --show-error --location "$compose_checksum_url" -o "$temporary_directory/compose-bundle.tar.gz.sha256"
mapfile -t checksum_records < "$temporary_directory/compose-bundle.tar.gz.sha256"
if [[ "${#checksum_records[@]}" -ne 1 ]] ||
  [[ ! "${checksum_records[0]:-}" =~ ^([a-f0-9]{64})[[:space:]]+\*?([^[:space:]]+)$ ]]; then
  printf 'The nightly Compose bundle failed its published SHA-256 check.\n' >&2
  exit 1
fi
checksum_digest="${BASH_REMATCH[1]}"
checksum_filename="${BASH_REMATCH[2]}"
actual_bundle_digest="$(sha256sum "$temporary_directory/compose-bundle.tar.gz" | cut -d ' ' -f 1)"
if [[ "$checksum_filename" != "$(basename "$compose_bundle_url")" || "$checksum_digest" != "$actual_bundle_digest" ]]; then
  printf 'The nightly Compose bundle failed its published SHA-256 check.\n' >&2
  exit 1
fi
bundle_root="$(tar --list --gzip --file "$temporary_directory/compose-bundle.tar.gz" | awk -F/ 'NF == 2 && $2 == "production.yml" { print $1; exit }')"
if [[ ! "$bundle_root" =~ ^tandem-nightly-[0-9a-f]{40}$ ]]; then
  printf 'The nightly release Compose bundle has an invalid directory layout.\n' >&2
  exit 1
fi
tar --extract --gzip --file "$temporary_directory/compose-bundle.tar.gz" \
  --to-stdout "$bundle_root/production.yml" > "$temporary_directory/production.yml"
chmod 644 "$temporary_directory/production.yml"
tar --extract --gzip --file "$temporary_directory/compose-bundle.tar.gz" \
  --to-stdout "$bundle_root/.env.example" > "$temporary_directory/nightly-env.example"
if grep -Eq '^[[:space:]]*build:' "$temporary_directory/production.yml"; then
  printf 'The nightly Compose bundle must use an image and must not build the API locally.\n' >&2
  exit 1
fi
mapfile -t api_images < <(sed -nE 's/^[[:space:]]*image:[[:space:]]*(ghcr\.io\/nischay-pro\/tandem-api@sha256:[a-f0-9]{64})[[:space:]]*$/\1/p' "$temporary_directory/production.yml")
if [[ "${#api_images[@]}" -ne 1 ]]; then
  printf 'The nightly Compose bundle must contain exactly one digest-pinned Tandem API image.\n' >&2
  exit 1
fi
if grep -Eq '^TANDEM_API_IMAGE=' "$temporary_directory/nightly-env.example"; then
  printf 'The nightly Compose bundle must not require a mutable API image setting.\n' >&2
  exit 1
fi
for support_file in "${support_files[@]}"; do
  support_temporary="$temporary_directory/$support_file"
  mkdir -p "$(dirname "$support_temporary")"
  curl --fail --silent --show-error --location "$deploy_base_url/$support_file" -o "$support_temporary"
  if [[ "$support_file" == *.sh ]]; then
    chmod 755 "$support_temporary"
  else
    chmod 644 "$support_temporary"
  fi
done
read -r -p 'Actual server URL reachable by Tandem [blank for per-user sign-in]: ' actual_server_url
read -r -p 'Public Tandem URL for Actual sign-in [optional]: ' actual_public_url
read -r -p 'Reverse proxy IP/CIDR [blank disables forwarded-address trust]: ' trust_proxy

ensure_secret() {
  local name="$1"
  local value="$2"
  local mode="${3:-0440}"
  local destination="$secrets_directory/$name"
  local temporary="$temporary_directory/$name"
  if [[ -e "$destination" ]]; then
    return
  fi
  printf '%s' "$value" > "$temporary"
  chmod "$mode" "$temporary"
  sudo chown root:1000 "$temporary"
  if ! ln "$temporary" "$destination" 2>/dev/null; then
    if [[ ! -e "$destination" ]]; then
      printf 'Could not safely create secret file %s.\n' "$destination" >&2
      exit 1
    fi
  fi
}

postgres_password_file="$secrets_directory/postgres_password"
if [[ -e "$postgres_password_file" ]]; then
  postgres_password="$(sudo cat "$postgres_password_file")"
  if [[ -z "$postgres_password" ]]; then
    printf 'Existing Postgres password file is empty; refusing to replace or initialize it.\n' >&2
    exit 1
  fi
else
  postgres_password="$(openssl rand -hex 32)"
  ensure_secret postgres_password "$postgres_password"
fi

ensure_secret database_url "postgresql://tandem:${postgres_password}@tandem-postgres:5432/tandem"
ensure_secret auth_token_encryption_key "$(openssl rand -hex 32)"
ensure_secret auth_token_encryption_key_previous ""
ensure_secret actual_session_token ""
unset postgres_password

TANDEM_ACTUAL_SERVER_URL_OVERRIDE="$actual_server_url" \
TANDEM_ACTUAL_PUBLIC_URL_OVERRIDE="$actual_public_url" \
TANDEM_TRUST_PROXY_OVERRIDE="$trust_proxy" \
awk '
  BEGIN {
    overrides["TANDEM_POSTGRES_DB"] = "tandem"
    overrides["TANDEM_POSTGRES_USER"] = "tandem"
    overrides["TANDEM_POSTGRES_PASSWORD_FILE"] = "deploy/compose/secrets/postgres_password"
    overrides["TANDEM_DATABASE_URL_FILE"] = "deploy/compose/secrets/database_url"
    overrides["TANDEM_ACTUAL_SESSION_TOKEN_FILE"] = "deploy/compose/secrets/actual_session_token"
    overrides["TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE"] = "deploy/compose/secrets/auth_token_encryption_key"
    overrides["TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE"] = "deploy/compose/secrets/auth_token_encryption_key_previous"
    overrides["TANDEM_TRUST_PROXY"] = ENVIRON["TANDEM_TRUST_PROXY_OVERRIDE"]
    overrides["TANDEM_EDGE_NETWORK"] = "tandem-edge"
    overrides["TANDEM_ACTUAL_SERVER_URL"] = ENVIRON["TANDEM_ACTUAL_SERVER_URL_OVERRIDE"]
    overrides["TANDEM_ACTUAL_PUBLIC_URL"] = ENVIRON["TANDEM_ACTUAL_PUBLIC_URL_OVERRIDE"]
    required = "TANDEM_POSTGRES_DB TANDEM_POSTGRES_USER TANDEM_POSTGRES_PASSWORD_FILE TANDEM_DATABASE_URL_FILE TANDEM_ACTUAL_SESSION_TOKEN_FILE TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE TANDEM_TRUST_PROXY TANDEM_EDGE_NETWORK TANDEM_ACTUAL_SERVER_URL TANDEM_ACTUAL_PUBLIC_URL"
  }
  {
    separator = index($0, "=")
    if (separator == 0) {
      print
      next
    }
    key = substr($0, 1, separator - 1)
    if (key in overrides) {
      print key "=" overrides[key]
      seen[key] = 1
    } else {
      print
    }
  }
  END {
    count = split(required, keys, " ")
    for (i = 1; i <= count; i++) {
      if (!(keys[i] in seen)) {
        print "Nightly Compose environment is missing " keys[i] "." > "/dev/stderr"
        exit 1
      }
    }
  }
' "$temporary_directory/nightly-env.example" > "$temporary_directory/.env"
chmod 600 "$temporary_directory/.env"

if [[ -e "$environment_file" ]]; then
  printf 'Settings already exist at %s; leaving them unchanged.\n' "$environment_file" >&2
  exit 1
fi
mv "$temporary_directory/production.yml" "$compose_file"
for support_file in "${support_files[@]}"; do
  mv "$temporary_directory/$support_file" "$deployment_directory/$support_file"
done
mv "$temporary_directory/.env" "$environment_file"

if ! docker network inspect tandem-edge >/dev/null 2>&1; then
  docker network create tandem-edge >/dev/null
fi

docker compose --project-directory "$install_directory" \
  --env-file "$environment_file" \
  -f "$compose_file" config --quiet

printf '\nSetup is ready. The API image is pinned by digest in %s.\n' "$compose_file"
printf 'Start Tandem with:\n  docker compose --project-directory %q --env-file %q -f %q up -d\n' \
  "$install_directory" "$environment_file" "$compose_file"
