#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
real_docker="$(command -v docker)"
test_root="$(mktemp -d)"
port_holder_pid=""
cleanup() {
  if [[ -n "$port_holder_pid" ]]; then
    kill "$port_holder_pid" >/dev/null 2>&1 || true
    wait "$port_holder_pid" >/dev/null 2>&1 || true
  fi
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
  mkdir -p "$root/install" "$root/actual-budget"
  printf 'TANDEM_API_HOST_PORT=19753\nTRUST_PROXY=\n' > "$root/install/.env"
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
printf 'y\n' | TANDEM_NGINX_TEST_CONFIG="$tls_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$tls_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$tls_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$tls_root/install" \
    > "$tls_root/first-run.log"

grep -Fq '# TANDEM-SETUP-MANAGED-ROUTES' "$tls_root/nginx.conf"
grep -Fq 'location ^~ /extension/v1/' "$tls_root/nginx.conf"
grep -Fq 'limit_req_zone $binary_remote_addr zone=tandem_setup_api:10m rate=600r/m;' "$tls_root/nginx.conf"
grep -Fq 'TRUST_PROXY=172.30.0.10' "$tls_root/install/.env"
grep -Fq 'tandem-edge:' "$tls_root/actual-budget/compose.override.yaml"
grep -Fq 'ipv4_address: "172.30.0.10"' "$tls_root/actual-budget/compose.override.yaml"
test ! -e "$tls_root/nginx.conf.tandem-setup-"*.bak

TANDEM_NGINX_TEST_CONFIG="$tls_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$tls_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$tls_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$tls_root/install" \
    > "$tls_root/second-run.log"
grep -Fq 'Tandem routes are already present' "$tls_root/second-run.log"
test "$(find "$tls_root" -name 'nginx.conf.tandem-setup-*.bak' -type f | wc -l)" -eq 0

http_root="$test_root/http-only"
make_install_tree "$http_root"
sed -i 's/listen \[::\]:443 ssl;/listen 80;/' "$http_root/nginx.conf"
TANDEM_NGINX_TEST_CONFIG="$http_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$http_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$http_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" \
    "$http_root/install" \
    > "$http_root/run.log"
grep -Fq 'No existing HTTPS Nginx vhost matched budget.nischay.pro' "$http_root/run.log"
test ! -e "$http_root/network-attached"
test ! -e "$http_root/actual-budget/compose.override.yaml"
if grep -Fq 'TANDEM-SETUP-MANAGED-ROUTES' "$http_root/nginx.conf"; then
  echo "The setup script changed an HTTP-only Nginx vhost." >&2
  exit 1
fi

rollback_root="$test_root/rollback"
make_install_tree "$rollback_root"
cp "$rollback_root/nginx.conf" "$rollback_root/original.conf"
printf 'y\n' | env \
  TANDEM_NGINX_TEST_CONFIG="$rollback_root/nginx.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$rollback_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$rollback_root/network-attached" \
  TANDEM_NGINX_TEST_FAIL_CONFIG=1 \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$rollback_root/install" \
    > "$rollback_root/run.log" 2>&1
cmp -s "$rollback_root/original.conf" "$rollback_root/nginx.conf"
grep -Fq 'Nginx validation failed' "$rollback_root/run.log"
test ! -e "$rollback_root/network-attached"

host_root="$test_root/host-nginx"
make_install_tree "$host_root"
ln -s nginx.conf "$host_root/sites-enabled-budget.conf"
mkdir -p "$host_root/install"
cat > "$host_root/install/docker-compose.yml" <<'YAML'
services:
  tandem-api:
    image: tandem-api:test
    expose:
      - "19753"
    networks:
      - tandem-edge
    ports:
      - "127.0.0.1:${TANDEM_API_HOST_PORT:-19753}:19753"

networks:
  tandem-edge:
    name: tandem-edge
    external: true
YAML
printf 'y\n' | TANDEM_NGINX_TEST_HOST=1 \
  TANDEM_NGINX_TEST_CONFIG="$host_root/nginx.conf" \
  TANDEM_NGINX_TEST_CONFIG_PATH="$host_root/sites-enabled-budget.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$host_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$host_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$host_root/install" \
    > "$host_root/run.log"
grep -Fq 'location ^~ /extension/v1/' "$host_root/nginx.conf"
grep -Fq 'proxy_pass http://127.0.0.1:19753;' "$host_root/nginx.conf"
! grep -Fq 'resolver 127.0.0.11' "$host_root/nginx.conf"
grep -Fq 'TRUST_PROXY=127.0.0.1/32,172.30.0.1/32' "$host_root/install/.env"
grep -Fq '127.0.0.1:${TANDEM_API_HOST_PORT:-19753}:19753' "$host_root/install/docker-compose.yml"
test -L "$host_root/sites-enabled-budget.conf"
test ! -e "$host_root/network-attached"
test ! -e "$host_root/nginx.conf.tandem-setup-"*.bak
"$real_docker" compose --project-directory "$host_root/install" \
  --env-file "$host_root/install/.env" -f "$host_root/install/docker-compose.yml" config --format json > "$host_root/compose.json"
jq -e '.services["tandem-api"].ports | any(.host_ip == "127.0.0.1" and .published == "19753" and .target == 19753)' \
  "$host_root/compose.json" >/dev/null
sed -i 's/^TANDEM_API_HOST_PORT=.*/TANDEM_API_HOST_PORT=19754/' \
  "$host_root/install/.env"
TANDEM_NGINX_TEST_HOST=1 \
  TANDEM_NGINX_TEST_CONFIG="$host_root/nginx.conf" \
  TANDEM_NGINX_TEST_CONFIG_PATH="$host_root/sites-enabled-budget.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$host_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$host_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$host_root/install" \
    > "$host_root/rerun.log"
grep -Fq 'proxy_pass http://127.0.0.1:19754;' "$host_root/nginx.conf"
"$real_docker" compose --project-directory "$host_root/install" \
  --env-file "$host_root/install/.env" -f "$host_root/install/docker-compose.yml" config --format json > "$host_root/compose-rerun.json"
jq -e '.services["tandem-api"].ports | any(.host_ip == "127.0.0.1" and .published == "19754" and .target == 19753)' \
  "$host_root/compose-rerun.json" >/dev/null

python3 - "$host_root/occupied-port" <<'PY' &
import socket
import sys
import time

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.bind(("127.0.0.1", 0))
sock.listen(1)
with open(sys.argv[1], "w", encoding="utf-8") as output:
    output.write(str(sock.getsockname()[1]))
time.sleep(60)
PY
port_holder_pid=$!
for _ in $(seq 1 100); do
  [[ -s "$host_root/occupied-port" ]] && break
  sleep 0.01
done
occupied_port="$(cat "$host_root/occupied-port")"
sed -i "s/^TANDEM_API_HOST_PORT=.*/TANDEM_API_HOST_PORT=$occupied_port/" \
  "$host_root/install/.env"
TANDEM_NGINX_TEST_HOST=1 \
  TANDEM_NGINX_TEST_CONFIG="$host_root/nginx.conf" \
  TANDEM_NGINX_TEST_CONFIG_PATH="$host_root/sites-enabled-budget.conf" \
  TANDEM_NGINX_TEST_COMPOSE_DIRECTORY="$host_root/actual-budget" \
  TANDEM_NGINX_TEST_NETWORK_STATE="$host_root/network-attached" \
  PATH="$fake_bin:$PATH" \
  bash "$project_root/deploy/configure-docker-nginx.sh" "$host_root/install" \
    > "$host_root/port-conflict.log"
selected_port="$(awk -F= '$1 == "TANDEM_API_HOST_PORT" { print $2; exit }' \
  "$host_root/install/.env")"
test "$selected_port" != "$occupied_port"
grep -Fq "Loopback port $occupied_port is already in use; selected free port $selected_port" \
  "$host_root/port-conflict.log"
grep -Fq "proxy_pass http://127.0.0.1:$selected_port;" "$host_root/nginx.conf"
"$real_docker" compose --project-directory "$host_root/install" \
  --env-file "$host_root/install/.env" -f "$host_root/install/docker-compose.yml" config --format json > "$host_root/compose-port-conflict.json"
jq -e --arg port "$selected_port" \
  '.services["tandem-api"].ports | any(.host_ip == "127.0.0.1" and .published == $port and .target == 19753)' \
  "$host_root/compose-port-conflict.json" >/dev/null
kill "$port_holder_pid" >/dev/null 2>&1 || true
wait "$port_holder_pid" >/dev/null 2>&1 || true
port_holder_pid=""

echo "Docker and host Nginx setup tests pass: route edits require approval, host API access stays on loopback, and HTTP-only vhosts stay unchanged."
