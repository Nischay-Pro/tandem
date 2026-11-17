#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
real_docker="$(command -v docker)"
test_root="$(mktemp -d)"
cleanup() {
  rm -rf -- "$test_root"
}
trap cleanup EXIT

fake_bin="$test_root/bin"
mkdir -p "$fake_bin"

cat > "$fake_bin/docker" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1-}" in
  ps)
    if [[ "${TANDEM_NGINX_TEST_HOST:-0}" != 1 ]]; then
      printf 'fake-nginx nginx:1.30-alpine\n'
    fi
    ;;
  exec)
    shift 2
    case "${1-} ${2-}" in
      "nginx -T")
        printf '# configuration file /etc/nginx/conf.d/default.conf:\n'
        cat "$TANDEM_NGINX_TEST_CONFIG"
        ;;
      "nginx -t") [[ "${TANDEM_NGINX_TEST_FAIL_CONFIG:-0}" != 1 ]] ;;
      "nginx -s") exit 0 ;;
      *) printf 'Unexpected docker exec command: %s\n' "$*" >&2; exit 1 ;;
    esac
    ;;
  inspect)
    network_json='{}'
    if [[ -e "$TANDEM_NGINX_TEST_NETWORK_STATE" ]]; then
      network_json='{"tandem-edge":{"IPAddress":"172.30.0.10"}}'
    fi
    jq -cn \
      --arg source "$TANDEM_NGINX_TEST_CONFIG" \
      --arg working_dir "$TANDEM_NGINX_TEST_COMPOSE_DIRECTORY" \
      --argjson networks "$network_json" \
      '[{
        Mounts:[{Source:$source,Destination:"/etc/nginx/conf.d/default.conf",RW:true}],
        NetworkSettings:{Networks:$networks},
        Config:{Labels:{"com.docker.compose.project.working_dir":$working_dir,"com.docker.compose.service":"nginx"}}
      }]'
    ;;
  network)
    if [[ "${2-}" == "inspect" ]]; then
      printf '[{"IPAM":{"Config":[{"Gateway":"172.30.0.1"}]}}]'
    elif [[ "${2-}" == "connect" ]]; then
      touch "$TANDEM_NGINX_TEST_NETWORK_STATE"
    fi
    ;;
  compose) exit 0 ;;
  *) printf 'Unexpected Docker command: %s\n' "$*" >&2; exit 1 ;;
esac
SH

cat > "$fake_bin/sudo" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
exec "$@"
SH

cat > "$fake_bin/nginx" <<'SH'
#!/usr/bin/env bash
set -euo pipefail
case "${1-} ${2-}" in
  "-T ")
    printf '# configuration file %s:\n' "$TANDEM_NGINX_TEST_CONFIG_PATH"
    cat "$TANDEM_NGINX_TEST_CONFIG"
    ;;
  "-t ") [[ "${TANDEM_NGINX_TEST_FAIL_CONFIG:-0}" != 1 ]] ;;
  "-s reload") exit 0 ;;
  *) printf 'Unexpected nginx command: %s\n' "$*" >&2; exit 1 ;;
esac
SH
chmod +x "$fake_bin/docker" "$fake_bin/sudo" "$fake_bin/nginx"

make_install_tree() {
  local root="$1"
  mkdir -p "$root/install/deploy/compose" "$root/actual-budget"
  printf 'TANDEM_TRUST_PROXY=\n' > "$root/install/deploy/compose/.env"
  cat > "$root/nginx.conf" <<'NGINX'
server {
    listen [::]:443 ssl;
    server_name budget.nischay.pro;
    ssl_certificate /etc/letsencrypt/live/budget/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/budget/privkey.pem;

    location / {
        proxy_pass http://actual:5006;
    }
}
NGINX
}

tls_root="$test_root/tls"
make_install_tree "$tls_root"
cp "$tls_root/nginx.conf" "$tls_root/original.conf"
TANDEM_NGINX_TEST_CONFIG="$tls_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$tls_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$tls_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$tls_root/install" "https://budget.nischay.pro" "https://budget.nischay.pro" "" \
    > "$tls_root/first-run.log"

grep -Fq '# TANDEM-SETUP-MANAGED-ROUTES' "$tls_root/nginx.conf"
grep -Fq 'location ^~ /api/v1/' "$tls_root/nginx.conf"
grep -Fq 'limit_req_zone $binary_remote_addr zone=tandem_setup_api:10m rate=600r/m;' "$tls_root/nginx.conf"
grep -Fq 'TANDEM_TRUST_PROXY=172.30.0.10' "$tls_root/install/deploy/compose/.env"
grep -Fq 'tandem-edge:' "$tls_root/actual-budget/compose.override.yaml"
grep -Fq 'ipv4_address: "172.30.0.10"' "$tls_root/actual-budget/compose.override.yaml"
cmp -s "$tls_root/original.conf" "$tls_root/nginx.conf.tandem-setup-"*.bak

TANDEM_NGINX_TEST_CONFIG="$tls_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$tls_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$tls_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$tls_root/install" "https://budget.nischay.pro" "https://budget.nischay.pro" "" \
    > "$tls_root/second-run.log"
grep -Fq 'Tandem routes are already present' "$tls_root/second-run.log"
test "$(find "$tls_root" -name 'nginx.conf.tandem-setup-*.bak' -type f | wc -l)" -eq 1

http_root="$test_root/http-only"
make_install_tree "$http_root"
sed -i 's/listen \[::\]:443 ssl;/listen 80;/' "$http_root/nginx.conf"
TANDEM_NGINX_TEST_CONFIG="$http_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$http_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$http_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$http_root/install" "https://budget.nischay.pro" "https://budget.nischay.pro" "" \
    > "$http_root/run.log"
grep -Fq 'No existing HTTPS Nginx vhost matched' "$http_root/run.log"
test ! -e "$http_root/network-attached"
test ! -e "$http_root/actual-budget/compose.override.yaml"
if grep -Fq 'TANDEM-SETUP-MANAGED-ROUTES' "$http_root/nginx.conf"; then
  echo "The setup script changed an HTTP-only Nginx vhost." >&2
  exit 1
fi

rollback_root="$test_root/rollback"
make_install_tree "$rollback_root"
cp "$rollback_root/nginx.conf" "$rollback_root/original.conf"
TANDEM_NGINX_TEST_CONFIG="$rollback_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$rollback_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$rollback_root/network-attached" \
  TANDEM_NGINX_TEST_FAIL_CONFIG=1 \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$rollback_root/install" "https://budget.nischay.pro" "https://budget.nischay.pro" "" \
    > "$rollback_root/run.log" 2>&1
cmp -s "$rollback_root/original.conf" "$rollback_root/nginx.conf"
grep -Fq 'Nginx validation failed; restored' "$rollback_root/run.log"
test ! -e "$rollback_root/network-attached"

host_root="$test_root/host-nginx"
make_install_tree "$host_root"
ln -s nginx.conf "$host_root/sites-enabled-budget.conf"
mkdir -p "$host_root/install/deploy/compose"
cat > "$host_root/install/deploy/compose/production.yml" <<'YAML'
services:
  tandem-api:
    image: tandem-api:test
    expose:
      - "19753"
    networks:
      - tandem-edge

networks:
  tandem-edge:
    name: tandem-edge
    external: true
YAML
cat > "$host_root/install/compose.yml" <<'YAML'
include:
  - path: ./deploy/compose/production.yml
    project_directory: .
    env_file: ./deploy/compose/.env
YAML
TANDEM_NGINX_TEST_HOST=1 \
  TANDEM_NGINX_TEST_CONFIG="$host_root/nginx.conf" \
  TANDEM_NGINX_TEST_CONFIG_PATH="$host_root/sites-enabled-budget.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$host_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$host_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$host_root/install" "https://budget.nischay.pro" "https://budget.nischay.pro" "" \
    > "$host_root/run.log"
grep -Fq 'location ^~ /api/v1/' "$host_root/nginx.conf"
grep -Fq 'proxy_pass http://127.0.0.1:19753;' "$host_root/nginx.conf"
! grep -Fq 'resolver 127.0.0.11' "$host_root/nginx.conf"
grep -Fq 'TANDEM_TRUST_PROXY=127.0.0.1/32,172.30.0.1/32' \
  "$host_root/install/deploy/compose/.env"
grep -Fq '# TANDEM-HOST-NGINX-PORT' "$host_root/install/compose.yml"
grep -Fq '127.0.0.1:19753:19753' "$host_root/install/compose.yml"
test -L "$host_root/sites-enabled-budget.conf"
test ! -e "$host_root/network-attached"
test -f "$host_root/nginx.conf.tandem-setup-"*.bak
"$real_docker" compose --project-directory "$host_root/install" \
  -f "$host_root/install/compose.yml" config --format json > "$host_root/compose.json"
jq -e '.services["tandem-api"].ports | any(.host_ip == "127.0.0.1" and .published == "19753" and .target == 19753)' \
  "$host_root/compose.json" >/dev/null

echo "Docker and host Nginx setup tests pass: TLS routes are backed up and validated, host API access stays on loopback, and HTTP-only vhosts stay unchanged."
