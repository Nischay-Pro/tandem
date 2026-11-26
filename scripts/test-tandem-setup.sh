#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_root="$(mktemp -d)"
trap 'rm -rf -- "$test_root"' EXIT
fake_bin="$test_root/bin"
mkdir -p "$fake_bin"

cat > "$fake_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1-} ${2-}" in
  "compose version") exit 0 ;;
  "network inspect") [[ -e "$TANDEM_SETUP_TEST_NETWORK" ]] ;;
  "network create") touch "$TANDEM_SETUP_TEST_NETWORK" ;;
  "compose --project-directory") exit 0 ;;
  *) printf 'Unexpected Docker command: %s\n' "$*" >&2; exit 1 ;;
esac
SH

cat > "$fake_bin/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
destination=""
url=""
while (($#)); do
  if [[ "$1" == "--output" ]]; then
    shift
    destination="$1"
  else
    url="$1"
  fi
  shift
done
file="${url##*/}"
if [[ "$file" == configure-docker-nginx.sh ]]; then
  cp "$TANDEM_SETUP_TEST_NGINX_HELPER" "$destination"
else
  cp "$TANDEM_SETUP_TEST_SOURCE/$file" "$destination"
fi
SH

cat > "$fake_bin/openssl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
printf '%064d' 1
SH

cat > "$test_root/nginx-helper" <<'SH'
#!/usr/bin/env bash
printf 'No matching HTTPS Nginx site was detected.\n'
SH
chmod +x "$fake_bin"/*

install_directory="$test_root/tandem"
printf 'latest-nightly\nhttp://actual:5006\n' |
  PATH="$fake_bin:$PATH" \
    TANDEM_INSTALL_DIRECTORY="$install_directory" \
    TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
    TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
    TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
    TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
    bash "$project_root/deploy/tandem-setup.sh" > "$test_root/first-run.log"

test -s "$install_directory/docker-compose.yml"
test -s "$install_directory/.env"
test "$(stat -c '%a' "$install_directory/.env")" = 600
grep -Fq 'TANDEM_API_TAG=latest-nightly' "$install_directory/.env"
grep -Fq 'POSTGRES_PASSWORD=' "$install_directory/.env"
test "$(grep -c '^ACTUAL_SERVER_URL=' "$install_directory/.env")" -eq 1
grep -Fqx 'ACTUAL_SERVER_URL=http://actual:5006' "$install_directory/.env"
! grep -Fq 'AUTH_TOKEN_ENCRYPTION_KEY=' "$install_directory/.env"
grep -Fq 'image: ghcr.io/nischay-pro/tandem-api:${TANDEM_API_TAG:-latest-nightly}' \
  "$install_directory/docker-compose.yml"
test "$(find "$install_directory" -mindepth 1 -maxdepth 1 -type f | wc -l)" -eq 2
! grep -Eq 'tandem-api@sha256|DATABASE_URL_FILE|_FILE=' \
  "$install_directory/docker-compose.yml" "$install_directory/.env"
test -e "$test_root/network"
grep -Fq 'docker compose pull' "$test_root/first-run.log"

environment_before="$(sha256sum "$install_directory/.env" | cut -d ' ' -f 1)"
printf 'stale Compose\n' > "$install_directory/docker-compose.yml"
PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$install_directory" \
  TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
  TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
  TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
  TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/second-run.log"
test "$environment_before" = "$(sha256sum "$install_directory/.env" | cut -d ' ' -f 1)"
grep -Fq 'Keeping existing .env unchanged.' "$test_root/second-run.log"
! grep -Fq 'stale Compose' "$install_directory/docker-compose.yml"

custom_directory="$test_root/custom"
printf 'my-tag\nhttps://budget.example.com/actual\n' |
  PATH="$fake_bin:$PATH" \
    TANDEM_INSTALL_DIRECTORY="$custom_directory" \
    TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
    TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
    TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
    TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
    bash "$project_root/deploy/tandem-setup.sh" > "$test_root/custom-run.log"
grep -Fq 'TANDEM_API_TAG=my-tag' "$custom_directory/.env"
grep -Fqx 'ACTUAL_SERVER_URL=https://budget.example.com/actual' "$custom_directory/.env"

sed -i 's/^ACTUAL_SERVER_URL=.*/ACTUAL_SERVER_URL=/' "$custom_directory/.env"
printf 'http://actual-server:5006\n' | PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$custom_directory" \
  TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
  TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
  TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
  TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/fill-url.log"
grep -Fqx 'ACTUAL_SERVER_URL=http://actual-server:5006' "$custom_directory/.env"
grep -Fq 'TANDEM_API_TAG=my-tag' "$custom_directory/.env"
test "$(stat -c '%a' "$custom_directory/.env")" = 600

if printf 'latest-nightly\nactual:5006\n' | PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$test_root/no-url" \
  TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
  TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
  TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
  TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/no-url.log" 2>&1; then
  echo 'Setup accepted an Actual server address without http:// or https://.' >&2
  exit 1
fi
test ! -e "$test_root/no-url/.env"

if printf 'bad@sha256\n' | PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$test_root/invalid" \
  TANDEM_SETUP_TEST_SOURCE="$project_root/deploy" \
  TANDEM_SETUP_TEST_NGINX_HELPER="$test_root/nginx-helper" \
  TANDEM_SETUP_TEST_NETWORK="$test_root/network" \
  TANDEM_DEPLOY_BASE_URL=https://downloads.invalid/deploy \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/invalid.log" 2>&1; then
  echo 'Setup accepted a digest instead of a Docker tag.' >&2
  exit 1
fi
test ! -e "$test_root/invalid/.env"

echo 'Tandem setup writes one root docker-compose.yml and .env, asks for the Actual server URL, refreshes Compose, preserves settings, and uses a mutable API tag.'
