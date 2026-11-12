#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
test_root="$(mktemp -d)"
cleanup() {
  rm -rf -- "$test_root"
}
trap cleanup EXIT

fake_bin="$test_root/bin"
install_directory="$test_root/install"
mkdir -p "$fake_bin" "$install_directory"

cat > "$fake_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1-} ${2-} ${3-}" in
  "compose version"*|"buildx version"*) exit 0 ;;
  "buildx imagetools inspect")
    printf '%s' '{"schemaVersion":2,"mediaType":"application/vnd.oci.image.index.v1+json"}'
    ;;
  "network inspect"*) exit 0 ;;
  "compose --project-directory"*) exit 0 ;;
  *)
    printf 'Unexpected Docker command in setup test: %s\n' "$*" >&2
    exit 1
    ;;
esac
SH

cat > "$fake_bin/curl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
destination=""
url=""
while (($#)); do
  if [[ "$1" == "-o" ]]; then
    shift
    destination="$1"
  else
    url="$1"
  fi
  shift
done
test -n "$destination"
case "$url" in
  */production.yml)
    cat > "$destination" <<'YAML'
services:
  tandem-api:
    image: ${TANDEM_API_IMAGE:?Run setup}
YAML
    ;;
  *.sh) printf '#!/usr/bin/env bash\nexit 0\n' > "$destination" ;;
  *.service|*.timer) printf '[Unit]\nDescription=Setup test\n' > "$destination" ;;
  *) printf '# Setup test fixture\n' > "$destination" ;;
esac
SH

cat > "$fake_bin/openssl" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
test "${1-}" = rand
test "${2-}" = -hex
test "${3-}" = 32
printf '%064d' 1
SH

cat > "$fake_bin/sudo" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
if [[ "${1-}" == "chown" ]]; then exit 0; fi
if [[ "${1-}" == "cat" ]]; then shift; exec cat "$@"; fi
printf 'Unexpected sudo command in setup test: %s\n' "$*" >&2
exit 1
SH

chmod +x "$fake_bin"/*
registry="ghcr.io/test/tandem-api"
manifest='{"schemaVersion":2,"mediaType":"application/vnd.oci.image.index.v1+json"}'
expected_digest="sha256:$(printf '%s' "$manifest" | sha256sum | cut -d ' ' -f 1)"
answers=$'http://actual:5006\nhttps://tandem.example.com\n10.0.0.2/32\n'

printf '%s' "$answers" |
  PATH="$fake_bin:$PATH" \
    TANDEM_INSTALL_DIRECTORY="$install_directory" \
    TANDEM_API_IMAGE_REPOSITORY="$registry" \
    TANDEM_DEPLOY_BASE_URL="https://downloads.invalid/deploy" \
    bash "$project_root/deploy/tandem-setup.sh" > "$test_root/first-run.log"

environment_file="$install_directory/deploy/compose/.env"
secret_directory="$install_directory/deploy/compose/secrets"
grep -Fq "TANDEM_API_IMAGE=$registry@$expected_digest" "$environment_file"
grep -Fq 'TANDEM_TRUST_PROXY=10.0.0.2/32' "$environment_file"
grep -Fq 'TANDEM_POSTGRES_PASSWORD_FILE=deploy/compose/secrets/postgres_password' "$environment_file"
test -s "$secret_directory/postgres_password"
test -s "$secret_directory/database_url"
test -s "$secret_directory/auth_token_encryption_key"
test ! -s "$secret_directory/auth_token_encryption_key_previous"
test ! -s "$secret_directory/actual_session_token"
test -x "$install_directory/deploy/backup.sh"
test -x "$install_directory/deploy/backup-scheduled.sh"
test -x "$install_directory/deploy/restore.sh"
test -s "$install_directory/deploy/systemd/tandem-backup.timer"
test -s "$install_directory/deploy/nginx/tandem-locations.conf.example"

environment_before="$(sha256sum "$environment_file" | cut -d ' ' -f 1)"
postgres_before="$(sha256sum "$secret_directory/postgres_password" | cut -d ' ' -f 1)"
encryption_key_before="$(sha256sum "$secret_directory/auth_token_encryption_key" | cut -d ' ' -f 1)"
printf '\n\n\n' |
  PATH="$fake_bin:$PATH" \
    TANDEM_INSTALL_DIRECTORY="$install_directory" \
    TANDEM_API_IMAGE_REPOSITORY="$registry" \
    TANDEM_DEPLOY_BASE_URL="https://downloads.invalid/deploy" \
    bash "$project_root/deploy/tandem-setup.sh" > "$test_root/second-run.log"

test "$environment_before" = "$(sha256sum "$environment_file" | cut -d ' ' -f 1)"
test "$postgres_before" = "$(sha256sum "$secret_directory/postgres_password" | cut -d ' ' -f 1)"
test "$encryption_key_before" = "$(sha256sum "$secret_directory/auth_token_encryption_key" | cut -d ' ' -f 1)"
grep -Fq 'Existing' "$test_root/second-run.log"
if grep -Eq '[0-9a-f]{64}' "$test_root/first-run.log"; then
  echo "The setup output exposed generated secret material." >&2
  exit 1
fi

echo "Tandem setup created digest-pinned config and preserved existing settings and secrets on rerun."
