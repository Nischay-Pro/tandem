# Tandem for Actual Budget

An unofficial, offline-first mobile companion for Actual Budget. Tandem provides Android and iOS apps while Actual remains the authoritative budget engine and data store.

**Tandem is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Overview

Tandem keeps a local copy of budget data on the phone so people can open a previously synced budget without a connection. It supports budget and account views, transaction creation and editing, conflict review, and settings for appearance, layout, motion, sync, and alerts.

Tandem is under active development and is not a production release. Android pairing, cached startup, transaction, and scheduled-sync flows are implemented. Android builds and Kotlin iOS frameworks compile; running and validating the iOS app still requires macOS and Xcode.

## How sync works

Actual remains the source of truth. The mobile apps connect to the self-hosted Tandem API, which uses the official [`@actual-app/api`](https://actualbudget.org/docs/api/) package to read and update Actual budgets. The Tandem API stores authorization, change-feed, and write-recovery state in PostgreSQL; it does not keep a second authoritative budget.

The mobile apps store snapshots in Room and refresh them through Tandem's snapshot and change-feed endpoints. Offline writes stay in a durable local outbox and are reconciled when the API is reachable. The app surfaces concurrent edits for review.

Actual's [sync system includes a CRDT implementation](https://actualbudget.org/docs/contributing/project-details/) for multi-device synchronization. Tandem's mobile apps do **not** implement that protocol; native clients such as [Actuali](https://github.com/MattFaz/actuali) take that direct-client approach. Actual's public API is a Node.js package, so Tandem keeps it in the API service rather than porting Actual's synchronization internals to Kotlin and Swift. This gives Tandem one place to enforce device scopes and reconcile financial writes, at the cost of requiring the Tandem service for sync. Cached data remains readable offline, and queued local edits wait for the service to reconnect.

## Requirements

- A reachable self-hosted Actual Budget server. The verified compatibility baseline is Actual `26.10.0` with `@actual-app/api` `26.10.0`.
- A running Tandem API service configured to connect to that Actual server.
- For backend development: `mise`, Docker Compose, and the tool versions pinned in `mise.toml`.
- For Android development: Android Studio, Android SDK, and the Java/Gradle toolchain documented in [`docs/mobile-api-client.md`](docs/mobile-api-client.md).
- For iOS development: macOS with Xcode.

## Getting started

### Start the Tandem API

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

### Build and run Android

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
