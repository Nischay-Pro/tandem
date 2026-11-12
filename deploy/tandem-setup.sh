#!/usr/bin/env bash
set -euo pipefail

readonly image_repository="${TANDEM_API_IMAGE_REPOSITORY:-ghcr.io/nischay-pro/tandem-api}"
readonly deploy_base_url="${TANDEM_DEPLOY_BASE_URL:-https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy}"
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

for required_command in curl docker openssl sha256sum sudo; do
  if ! command -v "$required_command" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$required_command" >&2
    exit 1
  fi
done

docker compose version >/dev/null
docker buildx version >/dev/null

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
curl --fail --silent --show-error --location "$deploy_base_url/compose/production.yml" -o "$temporary_directory/production.yml"
chmod 644 "$temporary_directory/production.yml"
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
mkdir -p "$temporary_directory/empty-docker-config"

image_manifest="$(DOCKER_CONFIG="$temporary_directory/empty-docker-config" docker buildx imagetools inspect --raw "$image_repository:nightly" 2>/dev/null)" || {
  printf 'Could not read the anonymous nightly image at %s:nightly. Check the registry package visibility.\n' \
    "$image_repository" >&2
  exit 1
}
image_digest="sha256:$(printf '%s' "$image_manifest" | sha256sum | cut -d ' ' -f 1)"
api_image="$image_repository@$image_digest"

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

cat > "$temporary_directory/.env" <<EOF
TANDEM_POSTGRES_DB=tandem
TANDEM_POSTGRES_USER=tandem
TANDEM_API_IMAGE=$api_image
TANDEM_POSTGRES_PASSWORD_FILE=deploy/compose/secrets/postgres_password
TANDEM_DATABASE_URL_FILE=deploy/compose/secrets/database_url
TANDEM_ACTUAL_SESSION_TOKEN_FILE=deploy/compose/secrets/actual_session_token
TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE=deploy/compose/secrets/auth_token_encryption_key
TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE=deploy/compose/secrets/auth_token_encryption_key_previous
TANDEM_TRUST_PROXY=$trust_proxy
TANDEM_EDGE_NETWORK=tandem-edge
TANDEM_ACTUAL_SERVER_URL=$actual_server_url
TANDEM_ACTUAL_PUBLIC_URL=$actual_public_url
EOF
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

printf '\nSetup is ready. The API image is pinned by digest in %s.\n' "$environment_file"
printf 'Start Tandem with:\n  docker compose --project-directory %q --env-file %q -f %q up -d\n' \
  "$install_directory" "$environment_file" "$compose_file"
