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
bundle_root="tandem-nightly-$(printf 'a%.0s' {1..40})"
mkdir -p "$test_root/$bundle_root"
expected_image="ghcr.io/nischay-pro/tandem-api@sha256:$(printf 'a%.0s' {1..64})"
node "$project_root/scripts/render-pinned-compose.mjs" "$expected_image" "$test_root/$bundle_root"
tar --create --gzip --file "$test_root/compose-bundle.tar.gz" -C "$test_root" "$bundle_root"

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
  https://api.github.com/repos/Nischay-Pro/tandem/releases/tags/nightly)
    printf '%s' '{"assets":[{"name":"Tandem-compose-0.1.0-nightly.1-old.tar.gz","browser_download_url":"https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-0.1.0-nightly.1-old.tar.gz","updated_at":"2026-01-01T00:00:00Z"},{"name":"Tandem-compose-0.1.0-nightly.2-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz","browser_download_url":"https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-0.1.0-nightly.2-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz","updated_at":"2026-01-02T00:00:00Z"},{"name":"Tandem-compose-0.1.0-nightly.2-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz.sha256","browser_download_url":"https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-0.1.0-nightly.2-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.tar.gz.sha256","updated_at":"2026-01-02T00:00:00Z"}]}' > "$destination"
    ;;
  https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-*.tar.gz.sha256)
    if [[ "${TANDEM_SETUP_TEST_BAD_CHECKSUM:-0}" == 1 ]]; then
      printf '%064d  %s\n' 0 "$(basename "${url%.sha256}")" > "$destination"
    else
      digest="$(sha256sum "$TANDEM_SETUP_TEST_BUNDLE" | cut -d ' ' -f 1)"
      printf '%s  %s\n' "$digest" "$(basename "${url%.sha256}")" > "$destination"
    fi
    ;;
  https://github.com/Nischay-Pro/tandem/releases/download/nightly/Tandem-compose-*.tar.gz)
    printf '%s\n' "$url" >> "${TANDEM_SETUP_TEST_URL_LOG:-/dev/null}"
    cp "$TANDEM_SETUP_TEST_BUNDLE" "$destination"
    ;;
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

cat > "$fake_bin/jq" <<'JS'
#!/usr/bin/env node
const fs = require("node:fs");
const release = JSON.parse(fs.readFileSync(process.argv.at(-1), "utf8"));
const bundle = release.assets
  .filter((asset) => asset.name.startsWith("Tandem-compose-") && asset.name.endsWith(".tar.gz"))
  .sort((left, right) => left.updated_at.localeCompare(right.updated_at))
  .at(-1);
const checksum = release.assets.find((asset) => asset.name === `${bundle?.name}.sha256`);
if (!bundle?.browser_download_url || !checksum?.browser_download_url) process.exit(1);
process.stdout.write(`${bundle.browser_download_url}\t${checksum.browser_download_url}`);
JS

chmod +x "$fake_bin"/*
answers=$'http://actual:5006\nhttps://tandem.example.com\n10.0.0.2/32\n'

printf '%s' "$answers" |
  PATH="$fake_bin:$PATH" \
    TANDEM_INSTALL_DIRECTORY="$install_directory" \
    TANDEM_DEPLOY_BASE_URL="https://downloads.invalid/deploy" \
    TANDEM_SETUP_TEST_BUNDLE="$test_root/compose-bundle.tar.gz" \
    TANDEM_SETUP_TEST_URL_LOG="$test_root/selected-url.log" \
    bash "$project_root/deploy/tandem-setup.sh" > "$test_root/first-run.log"

environment_file="$install_directory/deploy/compose/.env"
compose_file="$install_directory/deploy/compose/production.yml"
secret_directory="$install_directory/deploy/compose/secrets"
grep -Fq "image: $expected_image" "$compose_file"
grep -Fq 'Tandem-compose-0.1.0-nightly.2-' "$test_root/selected-url.log"
if grep -Fq 'TANDEM_API_IMAGE=' "$environment_file"; then
  echo "The setup command should use the nightly release's pinned Compose image." >&2
  exit 1
fi
grep -Fq 'TANDEM_TRUST_PROXY=10.0.0.2/32' "$environment_file"
grep -Fq 'TANDEM_ACTUAL_SERVER_URL=http://actual:5006' "$environment_file"
grep -Fq 'TANDEM_ACTUAL_PUBLIC_URL=https://tandem.example.com' "$environment_file"
grep -Fq 'TANDEM_EDGE_NETWORK=tandem-edge' "$environment_file"
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

failed_install_directory="$test_root/failed-install"
if PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$failed_install_directory" \
  TANDEM_DEPLOY_BASE_URL="https://downloads.invalid/deploy" \
  TANDEM_SETUP_TEST_BUNDLE="$test_root/missing-bundle.tar.gz" \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/failed-run.log" 2>&1; then
  echo "Setup unexpectedly continued without a nightly Compose bundle." >&2
  exit 1
fi
test ! -e "$failed_install_directory/deploy/compose/.env"
if find "$failed_install_directory/deploy/compose/secrets" -type f -print -quit 2>/dev/null | grep -q .; then
  echo "Setup created secret files before validating the nightly bundle." >&2
  exit 1
fi

checksum_failure_directory="$test_root/checksum-failure-install"
if PATH="$fake_bin:$PATH" \
  TANDEM_INSTALL_DIRECTORY="$checksum_failure_directory" \
  TANDEM_DEPLOY_BASE_URL="https://downloads.invalid/deploy" \
  TANDEM_SETUP_TEST_BUNDLE="$test_root/compose-bundle.tar.gz" \
  TANDEM_SETUP_TEST_BAD_CHECKSUM=1 \
  bash "$project_root/deploy/tandem-setup.sh" > "$test_root/checksum-failure.log" 2>&1; then
  echo "Setup unexpectedly continued with a mismatched Compose bundle checksum." >&2
  exit 1
fi
test ! -e "$checksum_failure_directory/deploy/compose/.env"
grep -Fq 'failed its published SHA-256 check' "$test_root/checksum-failure.log"

echo "Tandem setup installed the digest-pinned nightly bundle, preserved settings and secrets on rerun, and rejected unavailable or invalid bundles before installation."
