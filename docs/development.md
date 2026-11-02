# Development

## Local scratch data

`.dev-tmp/` is ignored by Git and is reserved for disposable development artifacts, database files, test fixtures, and exported images. Use synthetic data only. Docker images remain in the Docker daemon unless explicitly exported into `.dev-tmp/artifacts/`.

## API checks

The API checks run inside Docker because the development environment may not have Node.js installed. The toolchain is pinned to Node.js 26.13.0 LTS, pnpm 12.16.1, and PostgreSQL 18.6 on Alpine 3.24. These are the latest stable releases verified for this stack; PostgreSQL 19 is still a release candidate, so it is not used for this financial-data service.

```sh
docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
```

The integration-test profile runs formatting, lint, typecheck, and API tests against PostgreSQL. Coverage enforces 100% lines, statements, functions, and branches for API source. The development database is stored under `.dev-tmp/` and must contain synthetic data only.

Startup diagnostics include application-controlled configuration and migration
history messages. PostgreSQL driver failures and other unknown errors log only
their error type so connection details and credentials are not copied into logs.

GitHub Actions runs the same Compose quality gate for pushes and pull requests with read-only repository permissions.

StrykerJS mutation testing is kept out of the per-change gate because a full
run evaluates more than 1,600 mutants. `pnpm test:mutation` uses Stryker's
incremental results file under ignored `.dev-tmp/stryker/`; the first run
without a prior result still evaluates the full configured scope. Use
`pnpm test:mutation:full` to force every mutant to run after changes to the
mutation configuration, test environment, or dependencies. GitHub Actions
runs the forced full mutation suite weekly and on manual dispatch. Its HTML
report and temporary worktree stay under `.dev-tmp/`.

The generated KMP API client and its pinned Gradle toolchain are documented in
[`docs/mobile-api-client.md`](mobile-api-client.md). Android and JVM compilation
and tests run in the mobile CI job. iOS device and Apple Silicon simulator
compilation run on the macOS CI runner with Xcode. The local-first repository,
sync coordinator, and sync triggers are described in
[`docs/mobile-sync.md`](mobile-sync.md). The Android emulator configuration is
under `.dev-tmp/android-avd/` as `abc-data-api36` (Android 36, Google APIs,
x86_64); the SDK and system image are under `.dev-tmp/android-sdk/`. The AVD
was created for the Docker mount path `/workspace`, so its metadata must be
relocated or regenerated before running it directly from a differently rooted
host checkout. The previous Linux/Docker emulator test attempt did not leave a
connected emulator. A USB-authorized Pixel 10a is available for local app
debugging when attached. The shared Compose app can pair through Tandem, load
the authorized budget catalog, and sync budget data locally. Budget, account,
category, and activity views read that local copy. Editing an existing
transaction, creating transactions with stable caller-owned IDs, and reviewing
sync conflicts use the durable local write queue. For phone-to-host debug
pairing, use `adb reverse` to forward the app's loopback port to the local API.

### Android preview and Pixel debugging

Build and install the debug app from the `mobile/` directory:

```sh
export PATH="$PWD/../.dev-tmp/android-sdk/platform-tools:$PATH"
./gradlew :androidApp:assembleDebug
adb devices -l
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n app.tandemforactual.android/.MainActivity --ez preview true
```

The project uses the pinned Gradle 9.8.2 / Java 27 toolchain documented in
[`docs/mobile-api-client.md`](mobile-api-client.md). In this workspace, Gradle
and the Android SDK run in the configured Docker environment. Use normal ADB
touch, swipe, and Back inputs to inspect flows on the connected Pixel. Save
Do not commit wireframes or device captures; `.dev-tmp/` is ignored. The
`preview` launch extra opens the synthetic budget overview; without it the app
opens its welcome screen.
The ignored SDK copy currently contains Platform Tools 37.0.1, verified against
Google's SDK repository metadata; the host's default ADB may be older. See the
[Platform Tools release notes](https://developer.android.com/tools/releases/platform-tools)
when refreshing that local copy.

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

## Local household load fixture

The ignored Actual 26.10.0 compatibility server at
`.dev-tmp/actual/compat-server` contains two synthetic household budgets: one
CAD budget for the first year in Canada and one USD budget for the following
six years in the United States. Together they contain 15,000 synthetic
purchase rows, spanning apartment moves, rent changes, a four-year car loan,
flights, insurance, a wedding, pet surgery, and a home purchase. The local-only
seed script is `.dev-tmp/household-seed.mjs`; neither the script nor the budget
data belongs in Git. The Pixel 10a full-sync check stored 2,182 transaction
records for the CAD budget and 13,164 for the USD budget in Room. Those totals
include non-purchase transaction rows in addition to the 15,000 purchases. The
compatibility server also retains a separate older synthetic fixture, but the
Pixel test device is now scoped to the two household budget IDs and sees only
those two entries. An all-budgets test pairing would also expose the older
fixture; it is not part of the household dataset.

## Code quality

Run the Compose integration-test command above for the per-change gate. Running `pnpm check:all` directly also requires `DATABASE_URL` to point to a disposable PostgreSQL database. Run `pnpm test:mutation` for incremental mutation testing or `pnpm test:mutation:full` to force the full configured set.

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
