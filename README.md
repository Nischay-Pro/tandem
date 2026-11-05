# Tandem for Actual Budget

An unofficial, offline-first mobile companion for Actual Budget. Tandem provides Android and iOS apps while Actual remains the authoritative budget engine and data store.

**Tandem is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Overview

Tandem keeps a local copy of budget data on the phone so people can open a previously synced budget without a connection. It supports budget and account views, transaction creation and editing, conflict review, and settings for appearance, layout, motion, sync, and alerts.

Tandem is under active development and is not a production release. Android pairing, cached startup, transaction, and scheduled-sync flows are implemented. Android builds and Kotlin iOS frameworks compile; running and validating the iOS app still requires macOS and Xcode.

## How sync works

Actual remains the source of truth. The Android and iOS apps sync through the Tandem API and keep budget snapshots in Room, so previously synced data remains readable offline. The API uses Actual's official Node.js package to read and update budgets, while PostgreSQL stores Tandem's budget projection, change-feed cursors, and write-recovery state.

Tandem uses this service because Actual does not provide a budget REST API and its official programmatic API is Node.js-only. The service gives both mobile platforms one supported Actual integration and centralizes device pairing, budget access, and write reconciliation. It must be trusted with readable budget data, including when an Actual budget is end-to-end encrypted. See [Architectural design](docs/architectural-design.md) for the rationale, tradeoffs, and a side-by-side diagram of unencrypted and encrypted budget flows.

## Requirements

- A reachable self-hosted Actual Budget server. The verified compatibility baseline is Actual `26.11.0` with `@actual-app/api` `26.11.0`.
- Docker Engine with Docker Compose for a container deployment, or Node.js `26.13.0`, pnpm `12.16.1`, and PostgreSQL `18.6` for a host-process deployment.
- A reverse proxy with HTTPS for access from phones. Keep the Tandem API and PostgreSQL off the public internet; route the API through the same trusted proxy used for Actual.
- For Android development: Android Studio, Android SDK, and the Java/Gradle toolchain documented in [`docs/mobile-api-client.md`](docs/mobile-api-client.md).
- For iOS development: macOS with Xcode.

## Getting started

### 1. Set up Actual Budget

Set up a self-hosted Actual server using the [official installation guide](https://actualbudget.org/docs/install/). Use the supported `26.11.0` server release and have the server URL, server password, and budget Sync ID ready.

### 2. Run the Tandem API

Tandem does not publish a prebuilt API image yet. From the root of the Tandem repository, build the image on the deployment host (or build it elsewhere and transfer it there):

```sh
docker build \
  --tag tandem-for-actual-api:26.11.0-local \
  --file services/api/Dockerfile \
  .
```

#### If Actual already runs in Docker Compose

Change to the directory containing your existing `docker-compose.yml` (use its actual filename if different). Leave the existing Actual service configuration in place. Add the two Tandem service definitions under its existing `services:` section, and merge the `volumes:` and `networks:` entries below into the corresponding top-level sections. The example assumes your Actual service is named `actual-server` and listens on port `5006` inside Compose. Check the service name with `docker compose config --services`; set `ACTUAL_SERVER_URL` below to `http://<that-service-name>:5006` if it differs.

```yaml
services:
  tandem-postgres:
    image: postgres:18.6-alpine3.24
    restart: unless-stopped
    environment:
      POSTGRES_DB: tandem
      POSTGRES_USER: tandem
      POSTGRES_PASSWORD: ${TANDEM_DB_PASSWORD:?Set TANDEM_DB_PASSWORD in .env.tandem}
    volumes:
      - tandem-postgres-data:/var/lib/postgresql
    healthcheck:
      test:
        ["CMD-SHELL", 'pg_isready -U "$${POSTGRES_USER}" -d "$${POSTGRES_DB}"']
      interval: 5s
      timeout: 3s
      retries: 20
    networks:
      - tandem-data

  tandem-api:
    image: tandem-for-actual-api:26.11.0-local
    restart: unless-stopped
    init: true
    read_only: true
    tmpfs:
      - /tmp:rw,noexec,nosuid,size=16m
    cap_drop:
      - ALL
    security_opt:
      - no-new-privileges:true
    environment:
      DATABASE_URL: postgresql://tandem:${TANDEM_DB_PASSWORD:?Set TANDEM_DB_PASSWORD in .env.tandem}@tandem-postgres:5432/tandem
      HOST: 0.0.0.0
      PORT: 19753
      CHANGE_FEED_EPOCH: ${CHANGE_FEED_EPOCH:?Set CHANGE_FEED_EPOCH in .env.tandem}
      TRUST_PROXY: ${TRUST_PROXY:-}
      ACTUAL_SERVER_URL: ${ACTUAL_SERVER_URL:-http://actual-server:5006}
      ACTUAL_SERVER_PASSWORD: ${ACTUAL_SERVER_PASSWORD:?Set ACTUAL_SERVER_PASSWORD in .env.tandem}
      ACTUAL_API_DATA_DIR: /data/actual-api
    depends_on:
      tandem-postgres:
        condition: service_healthy
    volumes:
      - tandem-actual-workspace:/data/actual-api
    expose:
      - "19753"
    networks:
      - tandem-data
      - default

volumes:
  tandem-postgres-data:
  tandem-actual-workspace:

networks:
  tandem-data:
    internal: true
```

The API joins the Compose project's `default` network so it can reach Actual and a proxy in the same project. PostgreSQL is isolated on `tandem-data`. If Actual or the proxy uses a named network, replace `default` under `tandem-api` with the shared network name and ensure the Actual service and proxy join it too. `expose` makes the API available to containers on that network without publishing a host port. If the reverse proxy runs directly on the host, add `ports: ["127.0.0.1:19753:19753"]` to `tandem-api` and point the proxy at `127.0.0.1:19753`.

Keep Tandem's credentials in a separate, private `.env.tandem` file beside your Compose file. Generate a URL-safe database password with `openssl rand -hex 32` and a stable change-feed value with `openssl rand -hex 24`:

```dotenv
TANDEM_DB_PASSWORD=<paste-the-generated-database-password>
CHANGE_FEED_EPOCH=<paste-the-generated-change-feed-value>
ACTUAL_SERVER_URL=http://actual-server:5006
ACTUAL_SERVER_PASSWORD=<your-Actual-server-password>
TRUST_PROXY=
```

Replace the example Actual service name and password with the values for your server. Restrict this file to the deployment account with `chmod 600 .env.tandem`. Keep `CHANGE_FEED_EPOCH` stable across normal restarts; see [deployment and restore guidance](deploy/README.md) before changing it or restoring a backup. If the existing Actual stack has an `.env` file, pass both files so its current settings remain available and Tandem's values are added:

```sh
docker compose \
  --env-file .env \
  --env-file .env.tandem \
  -f docker-compose.yml \
  up -d tandem-postgres tandem-api
```

Use your actual Compose filename in place of `docker-compose.yml`. If your Actual Compose file reads values from an existing `.env`, include `--env-file .env` on each Compose command; if it does not, use only `--env-file .env.tandem`. Add an Nginx `/api/v1/` route to `http://tandem-api:19753` before the existing Actual catch-all. In [`deploy/nginx/tandem-upstreams.conf.example`](deploy/nginx/tandem-upstreams.conf.example), change the upstream target from `api:19753` to `tandem-api:19753` to match this example service name. Keep TLS termination in the existing proxy and set `TRUST_PROXY` to its exact container IP or a narrow subnet when you want Fastify to honor forwarded client addresses. Do not publish PostgreSQL or expose the API directly to the internet.

The image tag in the Compose service must match the tag used by `docker build`. When updating Tandem later, build from the Tandem repository root, then run Compose from the Actual deployment directory to recreate only the API service:

```sh
docker build --tag tandem-for-actual-api:26.11.0-local --file services/api/Dockerfile .
# From the directory containing your Actual Compose file:
docker compose --env-file .env --env-file .env.tandem -f docker-compose.yml up -d tandem-api
```

Check readiness through the HTTPS address configured in the reverse proxy:

```sh
curl --fail https://budget.example.com/api/v1/health/ready
```

Create a one-time pairing code scoped to the Actual budget you want on the device:

```sh
docker compose \
  --env-file .env \
  --env-file .env.tandem \
  -f docker-compose.yml \
  exec tandem-api \
  node dist-cli/bin/pairing-cli.js \
  --budget-id '<Actual budget sync ID>'
```

The code expires after five minutes and can only be used once. Grant only the budget IDs needed by that device. The phone must use the HTTPS URL exposed by the reverse proxy. See the [API guide](docs/api/README.md) for pairing scopes.

#### If you do not use the same Compose project as Actual

You can run Tandem in its own production Compose project. Clone this repository on the host, create the external network shared with Actual and your reverse proxy, configure `deploy/compose/.env`, then start the included stack. Its Compose file builds the API image and keeps PostgreSQL and the Actual workspace on persistent volumes:

```sh
docker network create tandem-edge
cp deploy/compose/.env.example deploy/compose/.env
chmod 600 deploy/compose/.env
# Edit deploy/compose/.env: set the DB credentials, stable change-feed epoch,
# Actual URL/password, and the external network name.
docker compose \
  --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml \
  up -d --build
```

Attach the Actual server and reverse proxy to `tandem-edge` (prefer adding the network to their Compose services so it survives container recreation). Set `ACTUAL_SERVER_URL` to the Actual service name and port on that network. Add the Tandem Nginx location before Actual's existing catch-all. The full network, proxy, backup, and restore steps are in [`deploy/README.md`](deploy/README.md).

#### If you do not run Docker

Install the pinned Node.js and pnpm versions from [`mise.toml`](mise.toml), plus PostgreSQL `18.6`. Create a dedicated PostgreSQL database and an OS account for Tandem. From the repository root, build the API and run it under a process supervisor such as systemd:

```sh
mise install
pnpm install --frozen-lockfile
pnpm --filter @tandem-for-actual/api build

sudo -u postgres createuser --pwprompt tandem
sudo -u postgres createdb --owner=tandem tandem
sudo install -d -o tandem -g tandem -m 0700 /var/lib/tandem/actual-api

export DATABASE_URL='postgresql://tandem:<url-safe-password>@127.0.0.1:5432/tandem'
export CHANGE_FEED_EPOCH='<stable-random-value>'
export HOST=127.0.0.1
export PORT=19753
export TRUST_PROXY=127.0.0.1
export ACTUAL_SERVER_URL='http://127.0.0.1:5006'
export ACTUAL_SERVER_PASSWORD='<your-Actual-server-password>'
export ACTUAL_API_DATA_DIR='/var/lib/tandem/actual-api'
node services/api/dist/entrypoint.js
```

Store these values in the supervisor's protected environment file instead of a shell history, keep `ACTUAL_API_DATA_DIR` on persistent storage, and place the API behind the HTTPS reverse proxy. Set `ACTUAL_SERVER_URL` to an address reachable from the host process. If Nginx runs on the same host, proxy `/api/v1/` to `http://127.0.0.1:19753` and keep `HOST=127.0.0.1`. Generate pairing codes with `node services/api/dist-cli/bin/pairing-cli.js --budget-id '<Actual budget sync ID>'`. The `pnpm api:dev` command is for development only; it does not replace a production process supervisor.

### 3. Build and run Android

From the `mobile/` directory:

```sh
./gradlew :androidApp:assembleDebug
adb reverse tcp:19753 tcp:19753
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Open Tandem, enter the HTTPS API address reachable from the Android device, and pair with the one-time code. For a local development API, use `adb reverse tcp:19753 tcp:19753` and `http://127.0.0.1:19753`; for a deployment, use the HTTPS URL configured in the reverse proxy.

### Build iOS

On macOS with Xcode installed, from the `mobile/` directory:

```sh
xcodebuild \
  -project iosApp/TandemForActual.xcodeproj \
  -scheme TandemForActual \
  -destination 'generic/platform=iOS Simulator' \
  build
```

## Development checks

Run the API checks in the disposable Docker environment:

```sh
pnpm test:integration
```

Verify the pinned Actual integration and production deployment workflow:

```sh
./scripts/test-actual-compatibility.sh
./scripts/test-production-deployment.sh
```

Integration checks use synthetic budgets. Project-owned executable API code has a 100% line-coverage gate. See [`docs/actual-compatibility.md`](docs/actual-compatibility.md), [`docs/mobile-sync.md`](docs/mobile-sync.md), and [`docs/development.md`](docs/development.md) for implementation and testing details.

## License

Licensed under AGPL-3.0-or-later. See [`LICENSE`](LICENSE).
