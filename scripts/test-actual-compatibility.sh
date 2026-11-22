#!/usr/bin/env bash
# Runs Tandem's direct-sync round trips against the pinned Actual server: budgets are seeded and encrypted with
# Actual's own client, then the Kotlin client edits them and checks Actual reads the same results back.
set -Eeuo pipefail

repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repository_root"

actual_image="ghcr.io/actualbudget/actual:26.11.0@sha256:06cf186569cf58d93e12659ff1a517b65b5b0ebee7050a3991d4bd8a1d398da6"
node_image="node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc"
container="tandem-actual-compat-$$"
port="${ACTUAL_COMPAT_HOST_PORT:-55006}"
password="compatibility-test-only-password"

cleanup() {
  docker rm --force "$container" >/dev/null 2>&1 || true
}
trap cleanup EXIT

docker run --detach --name "$container" --publish "127.0.0.1:$port:5006" "$actual_image" >/dev/null
for _ in $(seq 1 60); do
  curl --silent --fail "http://127.0.0.1:$port/" >/dev/null && break
  sleep 2
done
curl --silent --fail --header "Content-Type: application/json" \
  --data "{\"password\":\"$password\"}" "http://127.0.0.1:$port/account/bootstrap" >/dev/null

docker run --rm --network host --volume "$repository_root:/workspace" --workdir /workspace \
  --env ACTUAL_URL="http://127.0.0.1:$port" --env ACTUAL_PASSWORD="$password" \
  "$node_image" sh -ec '
    if [ ! -d services/api/node_modules/@actual-app/api ]; then
      npm install --global --silent pnpm@12.16.1
      pnpm install --silent --frozen-lockfile --filter @tandem-for-actual/api
    fi
    node scripts/actual-fixtures/seed-parity.mjs
    cd services/api && node scripts/encrypt-budget.ts
  '

cd mobile
ACTUAL_TEST_URL="http://127.0.0.1:$port" ACTUAL_TEST_PASSWORD="$password" ./gradlew \
  :api-client:jvmTest --rerun \
  --tests "*ActualEditsRoundTripTest" --tests "*ActualEncryptedRoundTripTest"
