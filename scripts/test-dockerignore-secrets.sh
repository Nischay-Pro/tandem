#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
context_root="$(mktemp -d "${TMPDIR:-/tmp}/tandem-dockerignore.XXXXXX")"
image="tandem-dockerignore-check:${BASHPID}"

cleanup() {
  docker image rm "$image" >/dev/null 2>&1 || true
  rm -rf -- "$context_root"
}
trap cleanup EXIT

mkdir -p "$context_root/deploy/compose" "$context_root/certs" "$context_root/secrets"
cp "$project_root/.dockerignore" "$context_root/.dockerignore"
cp "$project_root/deploy/compose/.env.example" "$context_root/deploy/compose/.env.example"
cat > "$context_root/Dockerfile" <<'EOF'
FROM node:26.13.0-trixie-slim
WORKDIR /payload
COPY . .
EOF
cat > "$context_root/.env" <<'EOF'
DATABASE_URL=postgresql://canary-root-password
EOF
cat > "$context_root/deploy/compose/.env" <<'EOF'
ACTUAL_SERVER_PASSWORD=canary-actual-password
EOF
cat > "$context_root/deploy/compose/.env.local" <<'EOF'
LOCAL_SECRET=canary-local-secret
EOF
cat > "$context_root/server.key" <<'EOF'
canary-private-key
EOF
cat > "$context_root/certs/server.pem" <<'EOF'
canary-certificate
EOF
cat > "$context_root/secrets/database.json" <<'EOF'
{"password":"canary-secret-directory"}
EOF

docker build --quiet --tag "$image" "$context_root" >/dev/null
docker run --rm --entrypoint /bin/sh "$image" -ec '
  for path in \
    /payload/.env \
    /payload/deploy/compose/.env \
    /payload/deploy/compose/.env.local \
    /payload/server.key \
    /payload/certs/server.pem \
    /payload/secrets/database.json; do
    if [ -e "$path" ]; then
      echo "A canary secret entered the Docker build context: $path" >&2
      exit 1
    fi
  done
  test -f /payload/deploy/compose/.env.example
'
echo "Docker build context excludes canary secrets and retains .env.example."
