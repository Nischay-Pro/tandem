# Tandem for Actual Budget

An unofficial, offline-first mobile client for Actual Budget. Tandem provides an open-source Android and iOS envelope-budget experience while Actual remains the authoritative budget engine and data store.

**This project is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Project status

Tandem is under active development and is not a production release. Android pairing, budget sync, offline cached startup, transaction flows, and scheduled background sync have been exercised on a Pixel 10a. The Android and iOS targets compile; iOS build and runtime verification still require macOS with Xcode.

The current compatibility baseline is Actual server `26.10.0` with `@actual-app/api` `26.10.0`. Production deployment checks and the disposable Actual compatibility integration pass against synthetic data.

## Current architectural direction

- Android and iOS: Kotlin Multiplatform shared domain/sync code, with native-facing UI built from a shared Compose Multiplatform design system.
- Visual direction: original finance UI with customizable light/dark/system appearance, accent palettes, and restrained backgrounds inspired by Morphe Manager's theming approach. No Morphe source or assets are reused.
- Tandem API: TypeScript, Node.js, and Fastify; PostgreSQL stores Tandem coordination state, not a second authoritative copy of the budget.
- Actual integration: official `@actual-app/api` only. No direct edits to Actual server files or SQLite databases.
- Verified compatibility baseline: Actual server `26.10.0` with `@actual-app/api` `26.10.0`. The Tandem checks Actual's reported server version before opening a budget and rejects other versions until they pass the disposable compatibility gate.
- Local-first behavior: completed budget snapshots are stored in Room on the device and can be opened while the Tandem server is unavailable. Sync resumes when the server and network are available.
- Android background sync: optional six-hour WorkManager refresh with connected-network or unmetered-network constraints. iOS registers a best-effort background refresh task; runtime scheduling has not yet been verified on an Apple device.
- Mobile flows include authorized budget selection, account and transaction views, transaction edits and creation, conflict review, and preferences for appearance, layout, motion, sync, and alerts.
- License: GNU AGPL v3 or later.

## Development

Install the versions pinned in `mise.toml`, then install the workspace dependencies with `pnpm install --frozen-lockfile`. The API and disposable integration environment are brought up with Docker Compose.

Run `./scripts/test-actual-compatibility.sh` to check the pinned Actual release against synthetic budget data. Run `./scripts/test-production-deployment.sh` to exercise the production container, pairing, backup/restore, and recovery workflow in an isolated deployment.

The project requires 100% line coverage for project-owned executable code. Changes also require regression coverage for fixed behavior and integration coverage across service boundaries. Financial integration tests must use a disposable Actual server and synthetic data only. See [`docs/actual-compatibility.md`](./docs/actual-compatibility.md) and [`docs/mobile-sync.md`](./docs/mobile-sync.md) for implementation details.

## License

Licensed under AGPL-3.0-or-later. See [`LICENSE`](./LICENSE).
