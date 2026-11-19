#!/usr/bin/env bash
set -euo pipefail

readonly deploy_base_url="${TANDEM_DEPLOY_BASE_URL:-https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy}"
default_directory="$PWD/tandem"

if [[ -n "${TANDEM_INSTALL_DIRECTORY:-}" ]]; then
  install_directory="$TANDEM_INSTALL_DIRECTORY"
else
  read -r -p "Install folder [$default_directory]: " install_directory
  install_directory="${install_directory:-$default_directory}"
fi
case "$install_directory" in
  '~') install_directory="${HOME:?}" ;;
  '~/'*) install_directory="${HOME:?}/${install_directory#~/}" ;;
esac
mkdir -p "$install_directory"
install_directory="$(cd "$install_directory" && pwd)"

compose_file="$install_directory/docker-compose.yml"
environment_file="$install_directory/.env"
temporary_directory="$(mktemp -d)"
cleanup() { rm -rf -- "$temporary_directory"; }
trap cleanup EXIT

for command in curl docker openssl; do
  if ! command -v "$command" >/dev/null 2>&1; then
    printf 'Required command not found: %s\n' "$command" >&2
    exit 1
  fi
done
docker compose version >/dev/null

for file in docker-compose.yml example.env configure-docker-nginx.sh; do
  curl --fail --silent --show-error --location \
    "$deploy_base_url/$file" --output "$temporary_directory/$file"
  test -s "$temporary_directory/$file"
done
bash -n "$temporary_directory/configure-docker-nginx.sh"

if [[ -L "$compose_file" || ( -e "$compose_file" && ! -f "$compose_file" ) ]]; then
  printf 'Cannot replace %s because it is not a regular file.\n' "$compose_file" >&2
  exit 1
fi
if [[ -L "$environment_file" || ( -e "$environment_file" && ! -f "$environment_file" ) ]]; then
  printf 'Cannot use %s because it is not a regular file.\n' "$environment_file" >&2
  exit 1
fi

if [[ ! -e "$environment_file" ]]; then
  read -r -p 'Tandem API image tag [latest-nightly]: ' api_tag
  api_tag="${api_tag:-latest-nightly}"
  if [[ ! "$api_tag" =~ ^[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}$ ]]; then
    printf 'Enter a Docker image tag, such as latest-nightly.\n' >&2
    exit 1
  fi

  postgres_password="$(openssl rand -hex 32)"
  encryption_key="$(openssl rand -hex 32)"
  cp "$temporary_directory/example.env" "$temporary_directory/.env"
  sed -i \
    -e "s/^TANDEM_API_TAG=.*/TANDEM_API_TAG=$api_tag/" \
    -e "s/^POSTGRES_PASSWORD=.*/POSTGRES_PASSWORD=$postgres_password/" \
    -e "s/^AUTH_TOKEN_ENCRYPTION_KEY=.*/AUTH_TOKEN_ENCRYPTION_KEY=$encryption_key/" \
    "$temporary_directory/.env"
  umask 077
  chmod 600 "$temporary_directory/.env"
  mv "$temporary_directory/.env" "$environment_file"
else
  printf 'Keeping existing .env unchanged.\n'
fi

install -m 0644 "$temporary_directory/docker-compose.yml" "$compose_file"

network_name="$(awk -F= '$1 == "TANDEM_EDGE_NETWORK" { print $2; exit }' "$environment_file")"
network_name="${network_name:-tandem-edge}"
if ! docker network inspect "$network_name" >/dev/null 2>&1; then
  docker network create "$network_name" >/dev/null
fi

if ! bash "$temporary_directory/configure-docker-nginx.sh" "$install_directory"; then
  printf 'Nginx could not be configured automatically. Review the reverse-proxy instructions before exposing Tandem.\n' >&2
fi

docker compose --project-directory "$install_directory" \
  --env-file "$environment_file" -f "$compose_file" config --quiet

printf '\nCreated %s and %s.\n' "$compose_file" "$environment_file"
printf 'Run:\n  cd %q\n  docker compose pull\n  docker compose down\n  docker compose up -d\n' "$install_directory"
