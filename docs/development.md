# Development

## Local scratch data

`.dev-tmp/` is ignored by Git and is reserved for disposable development artifacts, database files, test fixtures, and exported images. Use synthetic data only. Docker images remain in the Docker daemon unless explicitly exported into `.dev-tmp/artifacts/`.

## API checks

The API checks run inside Docker because the development environment may not have Node.js installed. The toolchain is pinned to Node.js 26.13.0 LTS, pnpm 12.16.1, and PostgreSQL 18.6 on Alpine 3.24. These are the latest stable releases verified for this stack; PostgreSQL 19 is still a release candidate, so it is not used for this financial-data service.

```sh
docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
```

The integration-test profile runs formatting, lint, typecheck, API tests against PostgreSQL, and StrykerJS mutation tests for Actual integration, reconciliation, device authorization, and budget locks. Coverage enforces 100% lines, statements, functions, and branches for API source. Mutation tests check whether behavioral regressions cause tests to fail; their HTML report and temporary worktree are written under `.dev-tmp/`. The development database is stored under `.dev-tmp/` and must contain synthetic data only.

GitHub Actions runs the same Compose quality gate for pushes and pull requests with read-only repository permissions.

The generated KMP API client and its pinned Gradle toolchain are documented in
[`docs/mobile-api-client.md`](mobile-api-client.md). Android and JVM compilation
and tests run in the mobile CI job. iOS device and Apple Silicon simulator
compilation run on the macOS CI runner with Xcode.

`services/api/Dockerfile.dev` is only for development and tests; it includes the full toolchain. The production image will be separate and contain runtime dependencies only.

Start the API with:

```sh
docker compose -f docker-compose.dev.yml up --build api
```

Then fetch `http://localhost:19753/api/v1/openapi.json`.

To enable the Actual-backed budget routes, provide `ACTUAL_SERVER_URL` and
`ACTUAL_SERVER_PASSWORD` to Compose, for example in an ignored local `.env`
file. The API accepts only the server release pinned in
`services/api/src/actual/actual-session.ts`. The API's local Actual workspace is
persisted under `.dev-tmp/actual/api-workspace`; it may hold decrypted budget
data. The server password stays in the API container. Encrypted budgets may
send their separate budget password per request in
`X-Budget-Encryption-Password`; that value is never written to PostgreSQL.
Treat the workspace, PostgreSQL data, and their backups as financial data.
Keep `CHANGE_FEED_EPOCH` stable during normal restarts. After restoring an older
PostgreSQL backup, change it to a new value before starting the API. This
invalidates prior mobile cursors and makes devices download a fresh server
snapshot; the epoch must be backed up separately from PostgreSQL.

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
in development and production. Because this release is still unreleased, the
current migration identity was changed when its schema expanded. A database
from the earlier pre-release identity now fails startup with an explicit history
mismatch; use a compatible build or recreate that pre-release Companion
database. The service never silently treats changed migration contents as
already applied.

## Journal data sensitivity

The journal persists base snapshots, proposed patches, and Actual result IDs in
PostgreSQL. These records can contain transaction amounts and notes, so protect
the database volume and backups as financial data. This slice does not add
application-level encryption or an API-blind end-to-end mode; the adapter must
read budget data to compare and reconcile changes. Never put Actual passwords,
budget encryption passwords, or device tokens into journal fields.
