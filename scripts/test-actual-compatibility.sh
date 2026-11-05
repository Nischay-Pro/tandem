#!/usr/bin/env bash
set -Eeuo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

scratch_root="$repository_root/.dev-tmp/actual"
compatibility_project="tandem-actual-compat"
compose_file="$repository_root/docker-compose.dev.yml"

clear_test_data() {
  python3 - "$scratch_root" <<'PY'
from pathlib import Path
import shutil
import sys

repository = Path.cwd().resolve()
expected_root = (repository / ".dev-tmp" / "actual").resolve()
scratch_root = Path(sys.argv[1]).resolve()
if scratch_root != expected_root:
    raise SystemExit("Refusing to clear a path outside .dev-tmp/actual")

for child_name in ("compat-gate-server", "compat-gate-client"):
    child = scratch_root / child_name
    if child.exists():
        shutil.rmtree(child)
    child.mkdir(parents=True, mode=0o700)
PY
  if [[ -d "$scratch_root/compat-gate-postgres" ]]; then
    docker run --rm --user 0:0 \
      --volume "$scratch_root/compat-gate-postgres:/compat-postgres" \
      --entrypoint sh node:26.13.0-trixie-slim -ec \
      'find /compat-postgres -mindepth 1 -maxdepth 1 -exec rm -rf -- {} +'
    rmdir "$scratch_root/compat-gate-postgres" 2>/dev/null || true
  fi
}

cleanup() {
  result=$?
  trap - EXIT
  docker compose -p "$compatibility_project" -f "$compose_file" \
    --profile actual-compat down --volumes --remove-orphans >/dev/null || true
  clear_test_data
  exit "$result"
}

trap cleanup EXIT
export ACTUAL_COMPAT_UID="$(id -u)"
export ACTUAL_COMPAT_GID="$(id -g)"
export POSTGRES_DATA_DIR="$scratch_root/compat-gate-postgres"
export POSTGRES_HOST_PORT=55433

docker compose -p "$compatibility_project" -f "$compose_file" \
  --profile actual-compat down --volumes --remove-orphans >/dev/null 2>&1 || true
clear_test_data

docker compose -p "$compatibility_project" -f "$compose_file" \
  --profile actual-compat run --build --rm actual-compat-tests
