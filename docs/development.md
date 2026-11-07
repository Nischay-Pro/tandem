# Development

## API checks

The API checks run inside Docker because the development environment may not have Node.js installed. The toolchain is pinned to Node.js 26.13.0 LTS, pnpm 12.16.1, and PostgreSQL 18.6 on Alpine 3.24. These are the latest stable releases verified for this stack; PostgreSQL 19 is still a release candidate, so it is not used for this financial-data service.

```sh
docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
```

The integration-test profile runs formatting, lint, typecheck, and API tests against PostgreSQL. Coverage enforces 100% lines, statements, functions, and branches for API source. Use a disposable PostgreSQL database with synthetic data only.

Startup diagnostics include application-controlled configuration and migration
history messages. PostgreSQL driver failures and other unknown errors log only
their error type so connection details and credentials are not copied into logs.

GitHub Actions runs the same Compose quality gate for pushes and pull requests with read-only repository permissions.

StrykerJS mutation testing is kept out of the per-change gate because a full
run evaluates more than 1,600 mutants. `pnpm test:mutation` uses Stryker's
incremental results file; the first run without a prior result still evaluates
the full configured scope. Use
`pnpm test:mutation:full` to force every mutant to run after changes to the
mutation configuration, test environment, or dependencies. GitHub Actions
runs the forced full mutation suite weekly and on manual dispatch. Its HTML
report and temporary worktree are generated locally.

The generated KMP API client and its pinned Gradle toolchain are documented in
[`docs/mobile-api-client.md`](mobile-api-client.md). Android and JVM compilation
and tests run in the mobile CI job. iOS device and Apple Silicon simulator
compilation run on the macOS CI runner with Xcode. The local-first repository,
sync coordinator, and sync triggers are described in
[`docs/mobile-sync.md`](mobile-sync.md). Use an Android emulator or connected
device configured through Android Studio or the Android SDK command-line tools.
The shared Compose app can pair through Tandem, load
the authorized budget catalog, and sync budget data locally. Budget, account,
category, and activity views read that local copy. Editing an existing
transaction, creating transactions with stable caller-owned IDs, and reviewing
sync conflicts use the durable local write queue. For phone-to-host debug
pairing, use `adb reverse` to forward the app's loopback port to the local API.

### Android preview and device debugging

Build and install the debug app from the `mobile/` directory. Ensure the Android
SDK platform-tools directory is on `PATH` so `adb` is available.

```sh
./gradlew :androidApp:assembleDebug
adb devices -l
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n app.tandemforactual.android/.MainActivity --ez preview true
```

The project uses the pinned Gradle 9.8.2 / Java 27 toolchain documented in
[`docs/mobile-api-client.md`](mobile-api-client.md). Gradle and the Android SDK
may be installed locally or supplied by a development container. Use ADB touch,
swipe, and Back inputs to inspect flows. Keep exported captures out of source
control. The `preview` launch extra opens the synthetic budget overview; without
it the app opens its welcome screen.

`services/api/Dockerfile.dev` is only for development and tests; it includes the full toolchain. The production image will be separate and contain runtime dependencies only.

Start the API with:

```sh
docker compose -f docker-compose.dev.yml up --build api
```

Then fetch `http://localhost:19753/api/v1/openapi.json`.

To enable the Actual-backed budget routes, provide `ACTUAL_SERVER_URL` and
`ACTUAL_SERVER_PASSWORD` to Compose, for example in an ignored local `.env`
file. Set `PAIRING_CODE_HASH_KEY` in that file when running the API or pairing
CLI directly; generate it with `openssl rand -hex 32` and use the same value
for both processes. The Docker development Compose file supplies a
development-only default. The API accepts only the server release pinned in
`services/api/src/actual/actual-session.ts`. The API's local Actual workspace may
hold decrypted budget data. The server password stays in the API container. Encrypted budgets may
send their separate budget password per request in
`X-Budget-Encryption-Password`; that value is never written to PostgreSQL.
Treat the workspace, PostgreSQL data, and their backups as financial data.
Keep `CHANGE_FEED_EPOCH` stable during normal restarts. After restoring an older
PostgreSQL backup, change it to a new value before starting the API. This
invalidates prior mobile cursors and makes devices download a fresh server
snapshot; the epoch must be backed up separately from PostgreSQL.

## Code quality

Run the Compose integration-test command above for the per-change gate. Running `pnpm check:all` directly also requires `DATABASE_URL` to point to a disposable PostgreSQL database and `PAIRING_CODE_HASH_KEY` to contain at least 32 bytes. Run `pnpm test:mutation` for incremental mutation testing or `pnpm test:mutation:full` to force the full configured set.

Install the local pre-commit checks once with:

```sh
./scripts/install-git-hooks.sh
```

The hook checks Prettier formatting and the mobile UI text and design-token
rules. When staged files include Kotlin, it also runs KtLint for the API
client, data, and Compose modules. CI remains the full verification gate.

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
mismatch; use a compatible build or recreate that pre-release Tandem
database. The service never silently treats changed migration contents as
already applied.

## Journal data sensitivity

The journal persists base snapshots, proposed patches, and Actual result IDs in
PostgreSQL. These records can contain transaction amounts and notes, so protect
the database volume and backups as financial data. This slice does not add
application-level encryption or an API-blind end-to-end mode; the adapter must
read budget data to compare and reconcile changes. Never put Actual passwords,
budget encryption passwords, or device tokens into journal fields.
