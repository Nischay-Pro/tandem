# Actual Budget Companion

An unofficial, open-source Android and iOS companion for Actual Budget. The mobile app is intended to provide a budget-first, envelope-style workflow while Actual remains the authoritative budget engine and data store.

**This project is not affiliated with, endorsed by, or supported by Actual Budget.** Actual Budget is a trademark of its respective owners.

## Project status

Early implementation. The initial wireframes are in [`actual-budget-companion-wireframes.png`](./actual-budget-companion-wireframes.png).

## Current architectural direction

- Android and iOS: Kotlin Multiplatform shared domain/sync code, with native-facing UI built from a shared Compose Multiplatform design system.
- Visual direction: original finance UI with customizable light/dark/system appearance, accent palettes, and restrained backgrounds inspired by Morphe Manager's theming approach. No Morphe source or assets are reused.
- Companion API: TypeScript, Node.js, and Fastify; PostgreSQL stores Companion coordination state, not a second authoritative copy of the budget.
- Actual integration: official `@actual-app/api` only. No direct edits to Actual server files or SQLite databases.
- Compatibility baseline: Actual server `26.10.0` with `@actual-app/api` `26.10.0` is the initial candidate pair. It is not declared supported until disposable-server integration tests pass.
- License: GNU AGPL v3 or later.

## Development

Install the versions pinned in `mise.toml`, then install the workspace dependencies with `pnpm install --frozen-lockfile`. The API and disposable integration environment are brought up with Docker Compose. Development scratch data belongs under `.dev-tmp/`; this directory is ignored by Git and must only contain synthetic test budgets and local artifacts.

The project requires 100% line coverage for project-owned executable code. Changes also require regression coverage for fixed behavior and integration coverage across service boundaries. Financial integration tests must use a disposable Actual server and synthetic data only.

## License

Licensed under AGPL-3.0-or-later. See [`LICENSE`](./LICENSE).
