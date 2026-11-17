#!/usr/bin/env bash
set -euo pipefail

readonly install_directory="${1:?Tandem install directory is required}"
readonly actual_server_url="${2:-}"
readonly public_tandem_url="${3:-}"
readonly requested_trust_proxy="${4:-}"
readonly environment_file="$install_directory/deploy/compose/.env"
readonly network_name="tandem-edge"

if ! command -v python3 >/dev/null 2>&1 || ! command -v jq >/dev/null 2>&1; then
  printf 'Skipping Docker Nginx auto-configuration: python3 or jq is unavailable.\n'
  exit 0
fi

url_for_domain="${public_tandem_url:-$actual_server_url}"
url_for_domain="${url_for_domain#*://}"
url_for_domain="${url_for_domain%%/*}"
url_for_domain="${url_for_domain%%:*}"
if [[ ! "$url_for_domain" =~ ^[A-Za-z0-9.-]+\.[A-Za-z]{2,}$ ]]; then
  printf 'Skipping Docker Nginx auto-configuration: no public domain could be derived.\n'
  exit 0
fi
readonly domain="${url_for_domain,,}"

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
        if not any(domain in name.split() for name in names):
            continue
        listens = re.findall(r"(?m)^\s*listen\s+([^;]+);", body)
        has_tls = any(
            re.search(r"(?:^|\s)(?:\[::\]:)?443(?:\s|$)", listen)
            and re.search(r"(?:^|\s)ssl(?:\s|$)", listen)
            for listen in listens
        ) or bool(re.search(r"(?m)^\s*ssl_certificate\s+", body))
        matches.append((path, has_tls, "TANDEM-SETUP-MANAGED-ROUTES" in body))

tls_matches = [item for item in matches if item[1]]
selected = tls_matches if tls_matches else matches
if len(selected) == 1:
    path, has_tls, managed = selected[0]
    print(f"{path}\t{int(has_tls)}\t{int(managed)}")
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

install_certbot_timer() {
  local container_name docker_binary unit_name service_source timer_source
  if ! command -v systemctl >/dev/null 2>&1 || ! sudo systemctl show-environment >/dev/null 2>&1; then
    printf 'Certificate issued, but no host systemd manager is available for automatic renewal.\n' >&2
    return 0
  fi
  container_name="$(docker inspect --format '{{.Name}}' "$nginx_container" | sed 's#^/##')"
  if [[ ! "$container_name" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    printf 'Certificate issued, but the Nginx container name is not safe for a renewal unit.\n' >&2
    return 0
  fi
  docker_binary="$(command -v docker)"
  unit_name="tandem-nginx-certbot-renew"
  if sudo test -e "/etc/systemd/system/$unit_name.service" ||
    sudo test -e "/etc/systemd/system/$unit_name.timer"; then
    printf 'A systemd unit named %s already exists; leaving it unchanged.\n' "$unit_name" >&2
    return 0
  fi
  service_source="$temporary_directory/$unit_name.service"
  timer_source="$temporary_directory/$unit_name.timer"
  cat > "$service_source" <<UNIT
[Unit]
Description=Renew Certbot certificates for Tandem Nginx
After=docker.service
Requires=docker.service

[Service]
Type=oneshot
ExecStart=$docker_binary exec $container_name certbot renew --quiet
UNIT
  cat > "$timer_source" <<UNIT
[Unit]
Description=Daily renewal check for Tandem Nginx certificates

[Timer]
OnCalendar=daily
RandomizedDelaySec=12h
Persistent=true

[Install]
WantedBy=timers.target
UNIT
  if sudo install -m 0644 "$service_source" "/etc/systemd/system/$unit_name.service" &&
    sudo install -m 0644 "$timer_source" "/etc/systemd/system/$unit_name.timer" &&
    sudo systemctl daemon-reload && sudo systemctl enable --now "$unit_name.timer"; then
    printf 'Installed the %s.timer systemd renewal schedule.\n' "$unit_name"
    return 0
  fi
  sudo systemctl disable --now "$unit_name.timer" >/dev/null 2>&1 || true
  sudo rm -f -- "/etc/systemd/system/$unit_name.service" "/etc/systemd/system/$unit_name.timer"
  sudo systemctl daemon-reload >/dev/null 2>&1 || true
  printf 'Certificate issued, but the automatic Certbot renewal timer could not be installed.\n' >&2
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

if ((${#nginx_matches[@]} == 0)); then
  if ((${#nginx_manager_images[@]} > 0)); then
    printf 'Detected a managed Nginx proxy (%s); leaving its generated configuration untouched.\n' \
      "${nginx_manager_images[0]}"
    printf 'Configure /api/v1/ in that proxy and route it to tandem-api:19753 on the tandem-edge network.\n'
  else
    printf 'No Docker Nginx server block matched %s; leaving proxy configuration unchanged.\n' "$domain"
  fi
  exit 0
fi
if ((${#nginx_matches[@]} != 1)); then
  printf 'More than one Docker Nginx server block matched %s; refusing to edit an ambiguous proxy.\n' "$domain" >&2
  exit 0
fi

IFS=$'\t' read -r nginx_container nginx_image config_match <<< "${nginx_matches[0]}"
IFS=$'\t' read -r container_config_path has_tls has_managed_routes <<< "$config_match"
if [[ "$container_config_path" == AMBIGUOUS || -z "$container_config_path" ]]; then
  printf 'Nginx has multiple matching server blocks for %s; refusing to edit an ambiguous proxy.\n' "$domain" >&2
  exit 0
fi
mount_mapping="$(map_container_config "$container_config_path")"
if [[ -z "$mount_mapping" ]]; then
  printf 'The matching Nginx configuration %s is not on a writable Docker mount; refusing a non-persistent edit.\n' \
    "$container_config_path"
  exit 0
fi

if [[ "$has_tls" != 1 ]]; then
  certbot_plugins="$temporary_directory/certbot-plugins.txt"
  if docker exec "$nginx_container" certbot plugins --text > "$certbot_plugins" 2>&1 &&
    grep -Eq '(^|[[:space:]])nginx([[:space:]]|$)' "$certbot_plugins"; then
    certbot_storage_mounted="$(docker inspect "$nginx_container" | jq -r '
      any(.[0].Mounts[]?; .RW == true and
        (.Destination == "/etc/letsencrypt" or (.Destination | startswith("/etc/letsencrypt/"))))
    ')"
    if [[ "$certbot_storage_mounted" != true ]]; then
      printf 'Certbot is installed, but /etc/letsencrypt is not on a writable Docker mount; refusing to issue a non-persistent certificate.\n' >&2
      exit 0
    fi
    read -r -p "Certbot email for $domain (blank skips certificate issuance): " certbot_email
    if [[ -n "$certbot_email" ]]; then
      docker exec "$nginx_container" certbot --nginx --non-interactive --agree-tos \
        --email "$certbot_email" --redirect -d "$domain"
      install_certbot_timer
      docker exec "$nginx_container" nginx -t
      dump_file="$temporary_directory/nginx-$nginx_container.conf"
      docker exec "$nginx_container" nginx -T > "$dump_file" 2>&1
      config_match="$(analyze_nginx_dump "$dump_file")"
      IFS=$'\t' read -r container_config_path has_tls has_managed_routes <<< "$config_match"
      mount_mapping="$(map_container_config "$container_config_path")"
      if [[ "$has_tls" != 1 || -z "$mount_mapping" ]]; then
        printf 'Certbot did not leave one persistent TLS vhost for %s; no Tandem route was added.\n' "$domain" >&2
        exit 0
      fi
    else
      printf 'No email supplied; Certbot did not run and the Nginx vhost was left unchanged.\n'
      exit 0
    fi
  else
    printf 'Nginx has no TLS vhost for %s and no Certbot Nginx plugin. Install Certbot in this Nginx container or add a TLS vhost first.\n' \
      "$domain"
    exit 0
  fi
fi

IFS=$'\t' read -r mount_source mount_destination <<< "$mount_mapping"
if [[ "$container_config_path" == "$mount_destination" ]]; then
  host_config_file="$mount_source"
else
  host_config_file="$mount_source/${container_config_path#"$mount_destination"/}"
fi
if ! sudo test -f "$host_config_file"; then
  printf 'Could not access the mounted Nginx configuration at %s; refusing to edit it.\n' "$host_config_file" >&2
  exit 0
fi

if [[ "$has_managed_routes" == 1 ]]; then
  printf 'Tandem routes are already present in the Nginx vhost for %s.\n' "$domain"
else
  route_file="$temporary_directory/tandem-locations.conf"
  cat > "$route_file" <<'NGINX'
    # TANDEM-SETUP-MANAGED-ROUTES
    location = /api/v1 {
        return 308 /api/v1/;
    }

    location ^~ /api/v1/auth/actual-return/ {
        access_log off;
        error_log /dev/null emerg;
        limit_req zone=tandem_setup_auth burst=5 nodelay;
        limit_req_status 429;
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $tandem_api tandem-api:19753;
        proxy_pass http://$tandem_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        add_header Cache-Control "no-store" always;
    }

    location = /api/v1/auth/login {
        limit_req zone=tandem_setup_auth burst=5 nodelay;
        limit_req_status 429;
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $tandem_api tandem-api:19753;
        proxy_pass http://$tandem_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        add_header Cache-Control "no-store" always;
    }

    location = /api/v1/auth/openid/start {
        limit_req zone=tandem_setup_auth burst=5 nodelay;
        limit_req_status 429;
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $tandem_api tandem-api:19753;
        proxy_pass http://$tandem_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        add_header Cache-Control "no-store" always;
    }

    location = /api/v1/auth/openid/complete {
        limit_req zone=tandem_setup_auth burst=5 nodelay;
        limit_req_status 429;
        resolver 127.0.0.11 valid=30s ipv6=off;
        set $tandem_api tandem-api:19753;
        proxy_pass http://$tandem_api;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        add_header Cache-Control "no-store" always;
    }

    location ^~ /api/v1/ {
        limit_req zone=tandem_setup_api burst=60 nodelay;
        limit_req_status 429;
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
  backup_path="$(sudo python3 - "$host_config_file" "$domain" "$route_file" <<'PY'
import os
import re
import shutil
import sys
import tempfile
from datetime import datetime, timezone

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
    has_tls = any(
        re.search(r"(?:^|\s)(?:\[::\]:)?443(?:\s|$)", listen)
        and re.search(r"(?:^|\s)ssl(?:\s|$)", listen)
        for listen in listens
    ) or bool(re.search(r"(?m)^\s*ssl_certificate\s+", body))
    if has_tls:
        matches.append((match.start(), opening, closing, body))

if len(matches) != 1:
    raise SystemExit("Could not identify exactly one TLS server block in the mounted Nginx file.")
server_start, opening, closing, body = matches[0]
if "TANDEM-SETUP-MANAGED-ROUTES" in body:
    print("")
    raise SystemExit(0)
if re.search(r"(?m)^\s*location\b[^{};]*?/api/v1", body):
    raise SystemExit("An existing /api/v1 route is present; refusing to replace it.")

mode = os.stat(config_path).st_mode & 0o777
metadata = os.stat(config_path)
backup = f"{config_path}.tandem-setup-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}.bak"
shutil.copy2(config_path, backup)
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
print(backup)
PY
)" || {
    printf 'Could not safely update the Nginx vhost for %s; it was not changed.\n' "$domain" >&2
    exit 0
  }
  if [[ -n "$backup_path" ]]; then
    if ! docker exec "$nginx_container" nginx -t; then
      sudo cp -- "$backup_path" "$host_config_file"
      printf 'Nginx validation failed; restored %s.\n' "$backup_path" >&2
      exit 0
    fi
    docker exec "$nginx_container" nginx -s reload
    printf 'Added Tandem API routes to %s (backup: %s).\n' "$domain" "$backup_path"
  fi
fi

network_json="$(docker inspect "$nginx_container" | jq -r '.[0].NetworkSettings.Networks // {}')"
if ! jq -e --arg network "$network_name" 'has($network)' <<< "$network_json" >/dev/null; then
  docker network connect "$network_name" "$nginx_container"
  printf 'Connected Nginx container %s to %s.\n' "$nginx_container" "$network_name"
fi

proxy_ip="$(docker inspect "$nginx_container" | jq -r --arg network "$network_name" '.[0].NetworkSettings.Networks[$network].IPAddress // empty')"
if [[ -n "$proxy_ip" && -z "$requested_trust_proxy" && -f "$environment_file" ]]; then
  temporary_environment="$temporary_directory/.env"
  TANDEM_TRUST_PROXY_AUTO="$proxy_ip" awk '
    BEGIN { updated = 0 }
    /^TANDEM_TRUST_PROXY=/ {
      print "TANDEM_TRUST_PROXY=" ENVIRON["TANDEM_TRUST_PROXY_AUTO"]
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
