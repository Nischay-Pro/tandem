# Tandem for Actual Budget

An independent, local-first mobile companion for Actual Budget. Tandem provides Android and iOS apps while Actual remains the authoritative budget engine and data store.

**Tandem is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Overview

Tandem keeps a local copy of budget data on the phone so people can open a previously synced budget without a connection. It supports budget and account views, transaction creation and editing, conflict review, and settings for appearance, layout, motion, sync, and alerts.

Tandem is under active development and is not a production release. Actual password and OpenID sign-in, cached startup, transaction, and scheduled-sync flows are implemented. Android builds and Kotlin iOS frameworks compile; running and validating the iOS app still requires macOS and Xcode.

## How sync works

Actual remains the source of truth. The Android and iOS apps sync through the Tandem API and keep budget snapshots in Room, so previously synced data remains readable offline. The API uses Actual's official Node.js package to read and update budgets, while PostgreSQL stores Tandem's budget projection, change-feed cursors, and write-recovery state.

Tandem uses this service because Actual does not provide a budget REST API and its official programmatic API is Node.js-only. The service gives both mobile platforms one supported Actual integration and centralizes sign-in, budget access, and write reconciliation. It must be trusted with readable budget data, including when an Actual budget is end-to-end encrypted. See [Architectural design](docs/architectural-design.md) for the rationale, tradeoffs, and a side-by-side diagram of unencrypted and encrypted budget flows.

## Requirements

- A reachable self-hosted Actual Budget server. The verified compatibility baseline is Actual `26.11.0` with `@actual-app/api` `26.11.0`.
- Docker Engine with Docker Compose for a container deployment, or Node.js `26.13.0`, pnpm `12.16.1`, and PostgreSQL `18.6` for a host-process deployment.
- A reverse proxy with HTTPS for access from phones. Keep the Tandem API and PostgreSQL off the public internet; route the API through the same trusted proxy used for Actual.
- For Android development: Android Studio, Android SDK, and the Java/Gradle toolchain documented in [`docs/mobile-api-client.md`](docs/mobile-api-client.md).
- For iOS development: macOS with Xcode.
- For mobile builds, install Node.js `26.13.0` and pnpm `12.16.1`; Gradle generates Tandem's offline merchant artwork from the pinned TheSVG package.

## Getting started

### 1. Set up Actual Budget

Set up a self-hosted Actual server using the [official installation guide](https://actualbudget.org/docs/install/). Use the supported `26.11.0` server release and have its address and your Actual sign-in ready.

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
    image: postgres:18.6-alpine3.24@sha256:123b17998ccaf47e53b502e8c6a1e79afd8f579c6f3eec83c563da026f957395
    restart: unless-stopped
    environment:
      POSTGRES_DB: tandem
      POSTGRES_USER: tandem
      POSTGRES_PASSWORD_FILE: /run/secrets/tandem_postgres_password
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
    secrets:
      - tandem_postgres_password

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
      DATABASE_URL_FILE: /run/secrets/tandem_database_url
      AUTH_TOKEN_ENCRYPTION_KEY_FILE: /run/secrets/tandem_auth_token_encryption_key
      HOST: 0.0.0.0
      PORT: 19753
      CHANGE_FEED_EPOCH: ${CHANGE_FEED_EPOCH:?Set CHANGE_FEED_EPOCH in .env.tandem}
      TRUST_PROXY: ${TRUST_PROXY:?Set TRUST_PROXY to the proxy IP or narrow CIDR in .env.tandem}
      ACTUAL_SERVER_URL: ${ACTUAL_SERVER_URL:-http://actual-server:5006}
      ACTUAL_PUBLIC_URL: ${ACTUAL_PUBLIC_URL:-}
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
    secrets:
      - tandem_database_url
      - tandem_auth_token_encryption_key

secrets:
  tandem_postgres_password:
    file: ${TANDEM_POSTGRES_PASSWORD_FILE:?Set TANDEM_POSTGRES_PASSWORD_FILE in .env.tandem}
  tandem_database_url:
    file: ${TANDEM_DATABASE_URL_FILE:?Set TANDEM_DATABASE_URL_FILE in .env.tandem}
  tandem_auth_token_encryption_key:
    file: ${TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE:?Set TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE in .env.tandem}

volumes:
  tandem-postgres-data:
  tandem-actual-workspace:

networks:
  tandem-data:
    internal: true
```

The API joins the Compose project's `default` network so it can reach Actual and a proxy in the same project. PostgreSQL is isolated on `tandem-data`. If Actual or the proxy uses a named network, replace `default` under `tandem-api` with the shared network name and ensure the Actual service and proxy join it too. `expose` makes the API available to containers on that network without publishing a host port. If the reverse proxy runs directly on the host, add `ports: ["127.0.0.1:19753:19753"]` to `tandem-api` and point the proxy at `127.0.0.1:19753`.

Tandem does not need a shared Actual server password. Each person signs in from the app with their Actual password or OpenID provider. Set `ACTUAL_PUBLIC_URL` to the public Tandem URL for OpenID; leave it empty if you only use password sign-in.

Keep Tandem's secret files in a private directory beside your Compose file. Compose mounts them read-only at `/run/secrets`; Tandem reads the values from those files, so they do not appear in the container environment. Create the directory and generate the database password, database URL, and encrypted-session key:

```sh
mkdir -m 700 -p .tandem-secrets
tandem_db_password="$(openssl rand -hex 32)"
printf '%s' "$tandem_db_password" > .tandem-secrets/postgres_password
printf 'postgresql://tandem:%s@tandem-postgres:5432/tandem' "$tandem_db_password" > .tandem-secrets/database_url
openssl rand -hex 32 > .tandem-secrets/auth_token_encryption_key
chmod 444 .tandem-secrets/*
unset tandem_db_password
```

Keep the directory private (`chmod 700 .tandem-secrets`); the files are readable inside the container because Compose file-backed secrets use the source file permissions. If Actual has no password, leave the file empty.

The separate `.env.tandem` file contains paths and non-secret settings only:

```dotenv
TANDEM_POSTGRES_PASSWORD_FILE=.tandem-secrets/postgres_password
TANDEM_DATABASE_URL_FILE=.tandem-secrets/database_url
TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE=.tandem-secrets/auth_token_encryption_key
CHANGE_FEED_EPOCH=<paste-the-generated-change-feed-value>
ACTUAL_SERVER_URL=http://actual-server:5006
ACTUAL_PUBLIC_URL=https://budget.example.com
# Required when using this reverse-proxy deployment; use the proxy IP or a
# narrow CIDR as seen by the Tandem API container.
TRUST_PROXY=
```

Set the Actual service name and URL for your Compose network. `ACTUAL_PUBLIC_URL` is the HTTPS address that reaches Tandem through your reverse proxy. The auth encryption key protects Actual session tokens stored by Tandem; keep it stable and private. Restrict `.env.tandem` to the deployment account with `chmod 600 .env.tandem`. Keep `CHANGE_FEED_EPOCH` stable across normal restarts; see [deployment and restore guidance](deploy/README.md) before changing it or restoring a backup. If the existing Actual stack has an `.env` file, pass both files so its current settings remain available and Tandem's paths are added:

```sh
docker compose \
  --env-file .env \
  --env-file .env.tandem \
  -f docker-compose.yml \
  up -d tandem-postgres tandem-api
```

Use your actual Compose filename in place of `docker-compose.yml`. If your Actual Compose file reads values from an existing `.env`, include `--env-file .env` on each Compose command; if it does not, use only `--env-file .env.tandem`. Add an Nginx `/api/v1/` route to `http://tandem-api:19753` before the existing Actual catch-all. In [`deploy/nginx/tandem-upstreams.conf.example`](deploy/nginx/tandem-upstreams.conf.example), change the upstream target from `api:19753` to `tandem-api:19753` to match this example service name. Keep TLS termination in the existing proxy and set the required `TRUST_PROXY` value to its exact container IP or a narrow subnet so Fastify can honor forwarded client addresses. Do not publish PostgreSQL or expose the API directly to the internet.

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

Open Tandem, enter the public Tandem API address, then sign in with the Actual password or OpenID option configured on your Actual server. Tandem lists the budgets available to that Actual account.

#### If you do not use the same Compose project as Actual

You can run Tandem in its own production Compose project. Clone this repository on the host, create the external network shared with Actual and your reverse proxy, configure `deploy/compose/.env`, then start the included stack. Its Compose file builds the API image and keeps PostgreSQL and the Actual workspace on persistent volumes:

```sh
docker network create tandem-edge
cp deploy/compose/.env.example deploy/compose/.env
chmod 600 deploy/compose/.env
# Edit deploy/compose/.env: set the DB credentials, auth encryption key,
# stable change-feed epoch, Actual URLs, and external network name.
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
export AUTH_TOKEN_ENCRYPTION_KEY='<at-least-32-byte-random-value>'
export CHANGE_FEED_EPOCH='<stable-random-value>'
export HOST=127.0.0.1
export PORT=19753
export TRUST_PROXY=127.0.0.1
export ACTUAL_SERVER_URL='http://127.0.0.1:5006'
export ACTUAL_API_DATA_DIR='/var/lib/tandem/actual-api'
export ACTUAL_PUBLIC_URL='https://budget.example.com'
node services/api/dist/entrypoint.js
```

Store environment values in the supervisor's protected environment file instead of a shell history, keep `ACTUAL_API_DATA_DIR` on persistent storage, and place the API behind the HTTPS reverse proxy. Set `ACTUAL_SERVER_URL` to the Actual address reachable from the host process. Set `ACTUAL_PUBLIC_URL` to the public Tandem address when enabling OpenID sign-in. If Nginx runs on the same host, proxy `/api/v1/` to `http://127.0.0.1:19753` and keep `HOST=127.0.0.1`. The `pnpm api:dev` command is for development only; it does not replace a production process supervisor.

### 3. Build and run Android

From the repository root, install the pinned JavaScript packages used by the mobile resource generator:

```sh
mise install
pnpm install --frozen-lockfile
```

Then from the `mobile/` directory:

```sh
./gradlew :androidApp:assembleDebug
adb reverse tcp:19753 tcp:19753
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Open Tandem, enter the HTTPS API address reachable from the Android device, then sign in to Actual. For a local development API, use `adb reverse tcp:19753 tcp:19753` and `http://127.0.0.1:19753`; for a deployment, use the HTTPS URL configured in the reverse proxy.

### Build iOS

After installing the pinned JavaScript packages above, on macOS with Xcode installed, from the `mobile/` directory:

```sh
xcodebuild \
  -project iosApp/TandemForActual.xcodeproj \
  -scheme TandemForActual \
  -destination 'generic/platform=iOS Simulator' \
  build
```

## Development checks

Install the local Git hooks once after cloning the repository:

```sh
./scripts/install-git-hooks.sh
```

The pre-commit hook checks staged formatting and mobile source rules. The pre-push hook checks repository formatting and Kotlin lint before pushing. Install project dependencies before running the hooks.

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
