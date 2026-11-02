# Tandem for Actual Budget

An unofficial, offline-first mobile client for Actual Budget. Tandem provides an open-source Android and iOS envelope-budget experience while Actual remains the authoritative budget engine and data store.

**This project is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Project status

Early implementation. Wireframes and device screenshots are temporary review artifacts under the ignored `.dev-tmp/` directory and are not part of the source distribution.

## Current architectural direction

- Android and iOS: Kotlin Multiplatform shared domain/sync code, with native-facing UI built from a shared Compose Multiplatform design system.
- Visual direction: original finance UI with customizable light/dark/system appearance, accent palettes, and restrained backgrounds inspired by Morphe Manager's theming approach. No Morphe source or assets are reused.
- Tandem API: TypeScript, Node.js, and Fastify; PostgreSQL stores Tandem coordination state, not a second authoritative copy of the budget.
- Actual integration: official `@actual-app/api` only. No direct edits to Actual server files or SQLite databases.
- Verified compatibility baseline: Actual server `26.10.0` with `@actual-app/api` `26.10.0`. The Tandem checks Actual's reported server version before opening a budget and rejects other versions until they pass the disposable compatibility gate.
- License: GNU AGPL v3 or later.

## Development

Install the versions pinned in `mise.toml`, then install the workspace dependencies with `pnpm install --frozen-lockfile`. The API and disposable integration environment are brought up with Docker Compose. Development scratch data belongs under `.dev-tmp/`; this directory is ignored by Git and must only contain synthetic test budgets and local artifacts.

The project requires 100% line coverage for project-owned executable code. Changes also require regression coverage for fixed behavior and integration coverage across service boundaries. Financial integration tests must use a disposable Actual server and synthetic data only.

## License

Licensed under AGPL-3.0-or-later. See [`LICENSE`](./LICENSE).
