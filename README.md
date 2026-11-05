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

- A reachable self-hosted Actual Budget server. The verified compatibility baseline is Actual `26.10.0` with `@actual-app/api` `26.10.0`.
- A running Tandem API service configured to connect to that Actual server.
- For backend development: `mise`, Docker Compose, and the tool versions pinned in `mise.toml`.
- For Android development: Android Studio, Android SDK, and the Java/Gradle toolchain documented in [`docs/mobile-api-client.md`](docs/mobile-api-client.md).
- For iOS development: macOS with Xcode.

## Getting started

### 1. Set up Actual Budget

Set up a self-hosted Actual server using the [official installation guide](https://actualbudget.org/docs/install/). Use the supported `26.10.0` server release and have the server URL, server password, and budget Sync ID ready.

### 2. Start the Tandem API

From the repository root, install the pinned tools and dependencies:

```sh
mise install
pnpm install --frozen-lockfile
```

Configure `ACTUAL_SERVER_URL` and `ACTUAL_SERVER_PASSWORD` in the environment used by Docker Compose. The API container must be able to reach the Actual server. Then start PostgreSQL and the API:

```sh
pnpm dev:up
```

Leave the service running. In another terminal, check that the API is ready:

```sh
curl --fail http://localhost:19753/api/v1/health/ready
```

Create a one-time pairing code scoped to the Actual budget you want to use:

```sh
docker compose -f docker-compose.dev.yml exec api \
  pnpm --filter @tandem-for-actual/api pair \
  --budget-id '<Actual budget sync ID>'
```

The code expires after five minutes and can only be used once. See the [API guide](docs/api/README.md) for budget scopes and deployment guidance.

### 3. Build and run Android

From the `mobile/` directory:

```sh
./gradlew :androidApp:assembleDebug
adb reverse tcp:19753 tcp:19753
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Open Tandem, enter the API address `http://127.0.0.1:19753` when using USB port forwarding, and pair with the one-time code. For a network install, use the API address reachable from the Android device instead.

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
