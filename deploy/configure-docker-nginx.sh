#!/usr/bin/env bash
set -euo pipefail

readonly install_directory="${1:?Tandem install directory is required}"
readonly environment_file="$install_directory/.env"
network_name="$(awk -F= '$1 == "TANDEM_EDGE_NETWORK" { print $2; exit }' "$environment_file" 2>/dev/null || true)"
network_name="${network_name:-tandem-edge}"
readonly network_name
requested_trust_proxy="$(awk -F= '$1 == "TRUST_PROXY" { print $2; exit }' "$environment_file" 2>/dev/null || true)"

if ! command -v python3 >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  printf 'Skipping Nginx auto-configuration: python3 or jq is unavailable.\n'
  exit 0
fi

domain=""
host_api_port="$(awk -F= '$1 == "TANDEM_API_HOST_PORT" { print $2; exit }' "$environment_file" 2>/dev/null || true)"
host_api_port="${host_api_port:-19753}"

temporary_directory="$(mktemp -d)"
cleanup() {
  rm -rf -- "$temporary_directory"
}
trap cleanup EXIT

analyze_nginx_dump() {
  python3 - "$1" "$domain" <<'PY'
import re
import sys

dump_path, domain = sys.argv[1:]
sections = {}
current_path = None
with open(dump_path, encoding="utf-8", errors="replace") as dump:
    for line in dump:
        marker = re.match(r"^# configuration file (.*):\s*$", line)
        if marker:
            current_path = marker.group(1)
            sections[current_path] = []
        elif current_path is not None:
            sections[current_path].append(line)

def matching_brace(text, opening):
    depth = 0
    quote = None
    escaped = False
    comment = False
    for index in range(opening, len(text)):
        character = text[index]
        if comment:
            if character == "\n":
                comment = False
            continue
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character == "#":
            comment = True
        elif character in "'\"":
            quote = character
        elif character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return index
    return None

matches = []
server_start = re.compile(r"\bserver\s*\{")
for path, lines in sections.items():
    text = "".join(lines)
    for match in server_start.finditer(text):
        opening = text.find("{", match.start())
        closing = matching_brace(text, opening)
        if closing is None:
            continue
        body = text[opening + 1 : closing]
        names = re.findall(r"(?m)^\s*server_name\s+([^;]+);", body)
        server_names = [candidate.lower() for name in names for candidate in name.split() if candidate != "_"]
        if domain and domain not in server_names:
            continue
        listens = re.findall(r"(?m)^\s*listen\s+([^;]+);", body)
        listens_on_443 = any(
            re.search(r"(?:^|\s)(?:\[[^]]+\]:|[^:\s]+:)?443(?:\s|$)", listen)
            for listen in listens
        )
        has_tls = listens_on_443 and (
            any(re.search(r"(?:^|\s)ssl(?:\s|$)", listen) for listen in listens)
            or bool(re.search(r"(?m)^\s*ssl\s+on\s*;", body))
            or bool(re.search(r"(?m)^\s*ssl_certificate\s+", body))
        )
        score = 0
        if any(re.search(r"actual|budget", name, re.I) for name in server_names):
            score += 2
        if re.search(r"proxy_pass\s+https?://[^;]*(?:actual|budget|:5006)", body, re.I):
            score += 2
        chosen_name = next((name for name in server_names if not name.startswith("www.")), "")
        matches.append((path, has_tls, "TANDEM-SETUP-MANAGED-ROUTES" in body, chosen_name, score))

tls_matches = [item for item in matches if item[1]]
selected = tls_matches if tls_matches else matches
if not domain and len(tls_matches) > 1:
    likely = [item for item in tls_matches if item[4] > 0]
    if likely:
        best_score = max(item[4] for item in likely)
        selected = [item for item in likely if item[4] == best_score]
if len(selected) == 1:
    path, has_tls, managed, chosen_name, _ = selected[0]
    print(f"{path}\t{int(has_tls)}\t{int(managed)}\t{chosen_name}")
elif len(selected) > 1:
    print("AMBIGUOUS")
PY
}

map_container_config() {
  docker inspect "$nginx_container" | jq -r --arg path "$1" '
    .[0].Mounts
    | map(. as $mount | select($mount.RW == true and
        ($path == $mount.Destination or ($path | startswith(($mount.Destination | rtrimstr("/")) + "/")))))
    | sort_by(.Destination | length)
    | last
    | if . == null then empty else [.Source, .Destination] | @tsv end
  '
}

update_managed_host_route_port() {
  sudo python3 - "$1" "$2" <<'PY'
import os
from pathlib import Path
import re
import sys
import tempfile

path = Path(sys.argv[1])
port = sys.argv[2]
text = path.read_text(encoding="utf-8")
start = text.find("# TANDEM-SETUP-MANAGED-ROUTES")
end = text.find("# END TANDEM-SETUP-MANAGED-ROUTES", start)
if start < 0 or end < 0:
    raise SystemExit("The managed Tandem route block is incomplete.")
end += len("# END TANDEM-SETUP-MANAGED-ROUTES")
routes = text[start:end]
updated_routes, replacements = re.subn(
    r"proxy_pass http://127\.0\.0\.1:[0-9]+;",
    f"proxy_pass http://127.0.0.1:{port};",
    routes,
)
if replacements == 0:
    raise SystemExit("No host Nginx upstream was found in the managed Tandem route block.")
if updated_routes == routes:
    raise SystemExit(0)
updated = text[:start] + updated_routes + text[end:]
metadata = path.stat()
fd, temporary = tempfile.mkstemp(prefix=".tandem-nginx-", dir=path.parent)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(updated)
    os.chown(temporary, metadata.st_uid, metadata.st_gid)
    os.chmod(temporary, metadata.st_mode & 0o777)
    os.replace(temporary, path)
except BaseException:
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    raise
print("changed")
PY
}

set_environment_value() {
  local key="$1"
  local value="$2"
  local temporary_environment="$temporary_directory/.env"
  TANDEM_ENVIRONMENT_KEY="$key" TANDEM_ENVIRONMENT_VALUE="$value" awk '
    BEGIN { updated = 0 }
    {
      prefix = ENVIRON["TANDEM_ENVIRONMENT_KEY"] "="
      if (!updated && index($0, prefix) == 1) {
        print prefix ENVIRON["TANDEM_ENVIRONMENT_VALUE"]
        updated = 1
        next
      }
      print
    }
    END {
      if (!updated) print ENVIRON["TANDEM_ENVIRONMENT_KEY"] "=" ENVIRON["TANDEM_ENVIRONMENT_VALUE"]
    }
  ' "$environment_file" > "$temporary_environment"
  chmod 600 "$temporary_environment"
  mv -- "$temporary_environment" "$environment_file"
}

select_host_api_port() {
  local tandem_api_port_is_published=false
  local api_container_id
  while read -r api_container_id; do
    [[ -n "$api_container_id" ]] || continue
    if docker inspect "$api_container_id" | jq -e --arg port "$host_api_port" '
      any(.[0].HostConfig.PortBindings["19753/tcp"][]?;
        .HostIp == "127.0.0.1" and .HostPort == $port)
    ' >/dev/null; then
      tandem_api_port_is_published=true
      break
    fi
  done < <(docker ps --quiet \
    --filter label=com.docker.compose.service=tandem-api \
    --filter "label=com.docker.compose.project.working_dir=$install_directory")

  if [[ "$tandem_api_port_is_published" != true ]] && ! python3 - "$host_api_port" <<'PY'
import socket
import sys

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    sock.bind(("127.0.0.1", int(sys.argv[1])))
except OSError:
    raise SystemExit(1)
finally:
    sock.close()
PY
  then
    local occupied_host_api_port="$host_api_port"
    host_api_port="$(python3 - <<'PY'
import socket

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
sock.bind(("127.0.0.1", 0))
print(sock.getsockname()[1])
sock.close()
PY
)"
    printf 'Loopback port %s is already in use; selected free port %s for Tandem.\n' \
      "$occupied_host_api_port" "$host_api_port"
  fi
  set_environment_value TANDEM_API_HOST_PORT "$host_api_port"
}

declare -a nginx_matches=()
declare -a nginx_manager_images=()
while read -r container_id container_image; do
  [[ -n "$container_id" ]] || continue
  container_image_lower="${container_image,,}"
  if [[ "$container_image_lower" == *nginx-proxy-manager* ||
    "$container_image_lower" == *nginx-proxy* ||
    "$container_image_lower" == *linuxserver/swag* ]]; then
    nginx_manager_images+=("$container_image")
    continue
  fi
  [[ "$container_image_lower" == *nginx* ]] || continue
  dump_file="$temporary_directory/nginx-$container_id.conf"
  if ! docker exec "$container_id" nginx -T > "$dump_file" 2>&1; then
    continue
  fi
  match="$(analyze_nginx_dump "$dump_file")"
  if [[ -n "$match" ]]; then
    nginx_matches+=("$container_id"$'\t'"$container_image"$'\t'"$match")
  fi
done < <(docker ps --no-trunc --format '{{.ID}} {{.Image}}')

proxy_mode="docker"
if ((${#nginx_matches[@]} == 0)) && command -v nginx >/dev/null 2>&1; then
  host_dump_file="$temporary_directory/host-nginx.conf"
  if sudo nginx -T > "$host_dump_file" 2>&1; then
    host_match="$(analyze_nginx_dump "$host_dump_file")"
    if [[ -n "$host_match" ]]; then
      nginx_matches+=("host"$'\t''nginx (host package)'$'\t'"$host_match")
      proxy_mode="host"
    fi
  fi
fi

if ((${#nginx_matches[@]} == 0)); then
  if ((${#nginx_manager_images[@]} > 0)); then
    printf 'Detected a managed Nginx proxy (%s); leaving its generated configuration untouched.\n' \
      "${nginx_manager_images[0]}"
    printf 'Configure /extension/v1/ in that proxy and route it to tandem-api:19753 on the tandem-edge network.\n'
  else
    printf 'No matching HTTPS Nginx site was detected; leaving Nginx unchanged.\n'
  fi
  exit 0
fi
if ((${#nginx_matches[@]} != 1)); then
  printf 'More than one HTTPS Nginx site could match; leaving Nginx unchanged.\n' >&2
  exit 0
fi

IFS=$'\t' read -r nginx_container nginx_image config_match <<< "${nginx_matches[0]}"
if [[ "$nginx_container" == host ]]; then
  proxy_mode="host"
fi
if [[ "$proxy_mode" == host ]]; then
  if [[ ! "$host_api_port" =~ ^[0-9]{1,5}$ ]] || ((host_api_port < 1 || host_api_port > 65535)); then
    printf 'TANDEM_API_HOST_PORT must be a port from 1 to 65535; refusing host Nginx setup.\n' >&2
    exit 0
  fi
fi
IFS=$'\t' read -r container_config_path has_tls has_managed_routes domain <<< "$config_match"
domain="${domain,,}"
if [[ "$container_config_path" == AMBIGUOUS || -z "$container_config_path" ]]; then
  printf 'Nginx has multiple matching server blocks for %s; refusing to edit an ambiguous proxy.\n' "$domain" >&2
  exit 0
fi
if [[ "$has_tls" != 1 ]]; then
  printf 'No existing HTTPS Nginx vhost matched %s; configure HTTPS with your existing setup, then rerun this setup.\n' \
    "$domain"
  exit 0
fi

if [[ "$proxy_mode" == docker ]]; then
  mount_mapping="$(map_container_config "$container_config_path")"
  if [[ -z "$mount_mapping" ]]; then
    printf 'The matching Nginx configuration %s is not on a writable Docker mount; refusing a non-persistent edit.\n' \
      "$container_config_path"
    exit 0
  fi
  IFS=$'\t' read -r mount_source mount_destination <<< "$mount_mapping"
  if [[ "$container_config_path" == "$mount_destination" ]]; then
    host_config_file="$mount_source"
  else
    host_config_file="$mount_source/${container_config_path#"$mount_destination"/}"
  fi
else
  host_config_file="$(sudo readlink -f -- "$container_config_path")" || {
    printf 'Could not resolve the host Nginx configuration path %s.\n' "$container_config_path" >&2
    exit 0
  }
fi
if ! sudo test -f "$host_config_file"; then
  printf 'Could not access the mounted Nginx configuration at %s; refusing to edit it.\n' "$host_config_file" >&2
  exit 0
fi

if [[ "$has_managed_routes" != 1 ]]; then
  answer=""
  read -r -p "Add Tandem routes to the detected Nginx site ${domain:-$host_config_file}? [y/N]: " answer || answer=""
  case "${answer,,}" in
    y|yes) ;;
    *) printf 'Leaving Nginx unchanged.\n'; exit 0 ;;
  esac
fi

if [[ "$proxy_mode" == host ]]; then
  select_host_api_port
fi

if [[ "$has_managed_routes" == 1 ]]; then
  if [[ "$proxy_mode" == host ]]; then
    sudo cp --preserve=mode,ownership -- "$host_config_file" "$temporary_directory/nginx-before.conf"
    route_changed="$(update_managed_host_route_port "$host_config_file" "$host_api_port")" || {
      printf 'Could not safely update the Tandem route to host port %s.\n' "$host_api_port" >&2
      exit 0
    }
    if [[ -n "$route_changed" ]]; then
      if ! sudo nginx -t; then
        sudo cp --preserve=mode,ownership -- "$temporary_directory/nginx-before.conf" "$host_config_file"
        printf 'Nginx validation failed. Check %s before reloading Nginx.\n' "$host_config_file" >&2
        exit 0
      fi
      if ! sudo nginx -s reload; then
        sudo cp --preserve=mode,ownership -- "$temporary_directory/nginx-before.conf" "$host_config_file"
        printf 'Nginx reload failed. Check %s before reloading Nginx.\n' "$host_config_file" >&2
        exit 0
      fi
      printf 'Updated Tandem routes to host port %s.\n' "$host_api_port"
    else
      printf 'Tandem routes are already present in the Nginx vhost for %s.\n' "$domain"
    fi
  else
    printf 'Tandem routes are already present in the Nginx vhost for %s.\n' "$domain"
  fi
else
  route_file="$temporary_directory/tandem-locations.conf"
  cat > "$route_file" <<'NGINX'
    # TANDEM-SETUP-MANAGED-ROUTES
    location ^~ /extension/v1/ {
        limit_req zone=tandem_setup_api burst=60 nodelay;
        limit_req_status 429;
        client_max_body_size 256k;
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $tandem_api tandem-api:19753;
        proxy_pass http://$tandem_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        add_header Cache-Control "no-store" always;
    }
    # END TANDEM-SETUP-MANAGED-ROUTES
NGINX
  if [[ "$proxy_mode" == host ]]; then
    python3 - "$route_file" "$host_api_port" <<'PY'
from pathlib import Path
import sys

path = Path(sys.argv[1])
port = sys.argv[2]
routes = path.read_text(encoding="utf-8")
routes = routes.replace("        resolver 127.0.0.11 valid=30s ipv6=off;\n", "")
routes = routes.replace("        set $tandem_api tandem-api:19753;\n", "")
routes = routes.replace(
    "        proxy_pass http://$tandem_api;\n",
    f"        proxy_pass http://127.0.0.1:{port};\n",
)
path.write_text(routes, encoding="utf-8")
PY
  fi
  sudo cp --preserve=mode,ownership -- "$host_config_file" "$temporary_directory/nginx-before.conf"
  route_changed="$(sudo python3 - "$host_config_file" "$domain" "$route_file" <<'PY'
import os
import re
import sys
import tempfile

config_path, domain, route_path = sys.argv[1:]
with open(config_path, encoding="utf-8") as stream:
    text = stream.read()
with open(route_path, encoding="utf-8") as stream:
    route = stream.read().rstrip("\n")

def matching_brace(source, opening):
    depth = 0
    quote = None
    escaped = False
    comment = False
    for index in range(opening, len(source)):
        character = source[index]
        if comment:
            if character == "\n":
                comment = False
            continue
        if quote:
            if escaped:
                escaped = False
            elif character == "\\":
                escaped = True
            elif character == quote:
                quote = None
            continue
        if character == "#":
            comment = True
        elif character in "'\"":
            quote = character
        elif character == "{":
            depth += 1
        elif character == "}":
            depth -= 1
            if depth == 0:
                return index
    return None

matches = []
for match in re.finditer(r"\bserver\s*\{", text):
    opening = text.find("{", match.start())
    closing = matching_brace(text, opening)
    if closing is None:
        continue
    body = text[opening + 1 : closing]
    names = re.findall(r"(?m)^\s*server_name\s+([^;]+);", body)
    if not any(domain in name.split() for name in names):
        continue
    listens = re.findall(r"(?m)^\s*listen\s+([^;]+);", body)
    listens_on_443 = any(
        re.search(r"(?:^|\s)(?:\[[^]]+\]:|[^:\s]+:)?443(?:\s|$)", listen)
        for listen in listens
    )
    has_tls = listens_on_443 and (
        any(re.search(r"(?:^|\s)ssl(?:\s|$)", listen) for listen in listens)
        or bool(re.search(r"(?m)^\s*ssl\s+on\s*;", body))
        or bool(re.search(r"(?m)^\s*ssl_certificate\s+", body))
    )
    if has_tls:
        matches.append((match.start(), opening, closing, body))

if len(matches) != 1:
    raise SystemExit("Could not identify exactly one TLS server block in the mounted Nginx file.")
server_start, opening, closing, body = matches[0]
if "TANDEM-SETUP-MANAGED-ROUTES" in body:
    print("")
    raise SystemExit(0)
if re.search(r"(?m)^\s*location\b[^{};]*?/extension/v1", body):
    raise SystemExit("An existing /extension/v1 route is present; refusing to replace it.")

mode = os.stat(config_path).st_mode & 0o777
metadata = os.stat(config_path)
updated = text[:closing] + "\n" + route + "\n" + text[closing:]
if "TANDEM-SETUP-MANAGED-LIMITS" not in text:
    limits = (
        "# TANDEM-SETUP-MANAGED-LIMITS\n"
        "limit_req_zone $binary_remote_addr zone=tandem_setup_auth:10m rate=10r/m;\n"
        "limit_req_zone $binary_remote_addr zone=tandem_setup_api:10m rate=600r/m;\n\n"
    )
    updated = updated[:server_start] + limits + updated[server_start:]
directory = os.path.dirname(config_path)
fd, temporary = tempfile.mkstemp(prefix=".tandem-nginx-", dir=directory)
try:
    with os.fdopen(fd, "w", encoding="utf-8") as stream:
        stream.write(updated)
    os.chown(temporary, metadata.st_uid, metadata.st_gid)
    os.chmod(temporary, mode)
    os.replace(temporary, config_path)
except BaseException:
    try:
        os.unlink(temporary)
    except FileNotFoundError:
        pass
    raise
print("changed")
PY
)" || {
    printf 'Could not safely update the Nginx vhost for %s; it was not changed.\n' "$domain" >&2
    exit 0
  }
  if [[ -n "$route_changed" ]]; then
    if [[ "$proxy_mode" == docker ]]; then
      nginx_test_command=(docker exec "$nginx_container" nginx -t)
      nginx_reload_command=(docker exec "$nginx_container" nginx -s reload)
    else
      nginx_test_command=(sudo nginx -t)
      nginx_reload_command=(sudo nginx -s reload)
    fi
    if ! "${nginx_test_command[@]}"; then
      sudo cp --preserve=mode,ownership -- "$temporary_directory/nginx-before.conf" "$host_config_file"
      printf 'Nginx validation failed. Check %s before reloading Nginx.\n' "$host_config_file" >&2
      exit 0
    fi
    if ! "${nginx_reload_command[@]}"; then
      sudo cp --preserve=mode,ownership -- "$temporary_directory/nginx-before.conf" "$host_config_file"
      printf 'Nginx reload failed. Check %s before reloading Nginx.\n' "$host_config_file" >&2
      exit 0
    fi
    printf 'Added Tandem API routes to %s.\n' "$domain"
  fi
fi

if [[ "$proxy_mode" == host ]]; then
  network_gateway="$(docker network inspect "$network_name" | jq -r '
    [.[0].IPAM.Config[]? | select((.Gateway // "") | test("^[0-9.]+$")) | .Gateway][0] // empty
  ')"
  if [[ -n "$network_gateway" && -z "$requested_trust_proxy" && -f "$environment_file" ]]; then
    trust_proxy_value="127.0.0.1/32,$network_gateway/32"
    temporary_environment="$temporary_directory/.env"
    TRUST_PROXY_DETECTED="$trust_proxy_value" awk '
      BEGIN { updated = 0 }
      /^TRUST_PROXY=/ {
        print "TRUST_PROXY=" ENVIRON["TRUST_PROXY_DETECTED"]
        updated = 1
        next
      }
      { print }
      END { if (!updated) exit 1 }
    ' "$environment_file" > "$temporary_environment"
    chmod 600 "$temporary_environment"
    mv -- "$temporary_environment" "$environment_file"
    printf 'Set Tandem proxy trust to the host Nginx loopback and Docker gateway addresses.\n'
  elif [[ -z "$requested_trust_proxy" ]]; then
    printf 'Could not detect the Docker gateway for host Nginx. TRUST_PROXY remains unchanged.\n' >&2
  fi
  printf 'Published the Tandem API on loopback port %s for host Nginx.\n' "$host_api_port"
  exit 0
fi

network_json="$(docker inspect "$nginx_container" | jq -r '.[0].NetworkSettings.Networks // {}')"
if ! jq -e --arg network "$network_name" 'has($network)' <<< "$network_json" >/dev/null; then
  docker network connect "$network_name" "$nginx_container"
  printf 'Connected Nginx container %s to %s.\n' "$nginx_container" "$network_name"
fi

proxy_ip="$(docker inspect "$nginx_container" | jq -r --arg network "$network_name" '.[0].NetworkSettings.Networks[$network].IPAddress // empty')"
if [[ -n "$proxy_ip" && -z "$requested_trust_proxy" && -f "$environment_file" ]]; then
  temporary_environment="$temporary_directory/.env"
  TRUST_PROXY_DETECTED="$proxy_ip" awk '
    BEGIN { updated = 0 }
    /^TRUST_PROXY=/ {
      print "TRUST_PROXY=" ENVIRON["TRUST_PROXY_DETECTED"]
      updated = 1
      next
    }
    { print }
    END { if (!updated) exit 1 }
  ' "$environment_file" > "$temporary_environment"
  chmod 600 "$temporary_environment"
  mv -- "$temporary_environment" "$environment_file"
  printf 'Set Tandem proxy trust to the detected Nginx address %s.\n' "$proxy_ip"
fi

compose_directory="$(docker inspect "$nginx_container" | jq -r '.[0].Config.Labels["com.docker.compose.project.working_dir"] // empty')"
compose_service="$(docker inspect "$nginx_container" | jq -r '.[0].Config.Labels["com.docker.compose.service"] // empty')"
if [[ -n "$compose_directory" && -n "$compose_service" && -d "$compose_directory" &&
  "$compose_service" =~ ^[A-Za-z0-9_-]+$ ]]; then
  override_file="$compose_directory/compose.override.yaml"
  if [[ ! -e "$override_file" ]]; then
    if [[ "$proxy_ip" =~ ^[0-9]{1,3}(\.[0-9]{1,3}){3}$ ]]; then
      cat > "$temporary_directory/compose.override.yaml" <<YAML
# Managed by Tandem setup to keep the Nginx network attachment after recreation.
services:
  $compose_service:
    networks:
      tandem-edge:
        ipv4_address: "$proxy_ip"

networks:
  tandem-edge:
    name: tandem-edge
    external: true
YAML
    else
      cat > "$temporary_directory/compose.override.yaml" <<YAML
# Managed by Tandem setup to keep the Nginx network attachment after recreation.
services:
  $compose_service:
    networks:
      tandem-edge: {}

networks:
  tandem-edge:
    name: tandem-edge
    external: true
YAML
    fi
    if install -m 0644 "$temporary_directory/compose.override.yaml" "$override_file"; then
      if docker compose --project-directory "$compose_directory" config --quiet; then
        printf 'Added a Compose override for the Nginx tandem-edge network in %s.\n' "$compose_directory"
      else
        rm -f -- "$override_file"
        printf 'Could not validate the generated Compose override; it was removed. The current Nginx container remains connected until it is recreated.\n' >&2
      fi
    fi
  elif grep -Fq 'Managed by Tandem setup' "$override_file"; then
    printf 'The Tandem Nginx Compose override is already present.\n'
  else
    printf 'A Compose override already exists at %s; the live network attachment may need to be repeated if Nginx is recreated.\n' \
      "$override_file"
  fi
fi
