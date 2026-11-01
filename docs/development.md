# Development

## Local scratch data

`.dev-tmp/` is ignored by Git and is reserved for disposable development artifacts, database files, test fixtures, and exported images. Use synthetic data only. Docker images remain in the Docker daemon unless explicitly exported into `.dev-tmp/artifacts/`.

## API checks

The API checks run inside Docker because the development environment may not have Node.js installed. The toolchain is pinned to Node.js 26.13.0 LTS, pnpm 12.16.1, and PostgreSQL 18.6 on Alpine 3.24. These are the latest stable releases verified for this stack; PostgreSQL 19 is still a release candidate, so it is not used for this financial-data service.

```sh
docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
```

The integration-test profile runs formatting, lint, typecheck, API tests against PostgreSQL, and StrykerJS mutation tests for the Actual API session lifecycle. Coverage enforces 100% lines, statements, functions, and branches for API source. Mutation tests check whether behavioral regressions cause tests to fail; their HTML report and temporary worktree are written under `.dev-tmp/`. The development database is stored under `.dev-tmp/` and must contain synthetic data only.

GitHub Actions runs the same Compose quality gate for pushes and pull requests with read-only repository permissions.

`services/api/Dockerfile.dev` is only for development and tests; it includes the full toolchain. The production image will be separate and contain runtime dependencies only.

Start the API with:

```sh
docker compose -f docker-compose.dev.yml up --build api
```

Then fetch `http://localhost:19753/api/v1/openapi.json`.

## Code quality

Run the Compose integration-test command above for the full gate. Running `pnpm check:all` directly also requires `DATABASE_URL` to point to a disposable PostgreSQL database. Run `pnpm test:mutation` from the repository root to rerun mutation testing independently.

## Database migrations

The current unreleased point release has one timestamped migration in
`services/api/src/schema/migrations/`, listed in that directory's `ORDER`
manifest. It has explicit `up` and `down` functions, runs transactionally at API
startup, and the migrator rejects database history that does not match the
manifest. Migration changes must include PostgreSQL integration coverage and
keep the manifest and registered migration list in sync. The migration is
copied into the compiled output by the API build so startup behavior is the same
in development and production.

## Journal data sensitivity

The journal persists base snapshots, proposed patches, and Actual result IDs in
PostgreSQL. These records can contain transaction amounts and notes, so protect
the database volume and backups as financial data. This slice does not add
application-level encryption or an API-blind end-to-end mode; the adapter must
read budget data to compare and reconcile changes. Never put Actual passwords,
budget encryption passwords, or device tokens into journal fields.
