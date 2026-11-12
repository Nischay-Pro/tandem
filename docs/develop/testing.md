# Test Tandem

For contributors checking API and mobile changes before review. Run the API
quality gate in its Compose environment and use CI for platform-specific checks.

## API quality gate

1. **Run the integration-test profile** from the repository root:

   ```sh
   docker compose -f docker-compose.dev.yml --profile test run --build --rm integration-tests
   ```

   It runs formatting, lint, type checks, API tests, and coverage against a
   disposable PostgreSQL database. Use synthetic data only.

   **Check:** the command exits successfully. If it fails, read the first
   failing check in its output, fix that issue, then rerun the command. For
   local API behavior with Actual, follow
   [Actual compatibility tests](actual-compatibility-tests.md).

The test profile uses Docker because it provides the API's pinned Node and
PostgreSQL versions. Direct `pnpm check:all` needs `DATABASE_URL` to point to a
disposable database and `AUTH_TOKEN_ENCRYPTION_KEY` to contain a randomly
generated 32-byte key encoded as 64 hexadecimal characters.

## Mobile checks

1. **Run the JVM and Android-host checks** from `mobile/`:

   ```sh
   ./gradlew \
     :api-client:jvmTest :api-client:ktlintCheck \
     :data:jvmTest :data:ktlintCheck \
     :composeApp:testAndroidHostTest :composeApp:ktlintCheck \
     :androidApp:testDebugUnitTest
   ```

   **Check:** every task succeeds. If Gradle cannot find Java or the Android
   SDK, use the [toolchain guide](toolchain.md) and set `ANDROID_HOME` if
   needed.

2. **Use CI for the full platform gate.** Push a branch and review the
   Android, iOS, API-client, and documentation jobs in GitHub Actions.

   **Check:** all required jobs pass. iOS simulator and Xcode host tests run
   on macOS; they are not verified by a Linux build. If a job fails, use its
   log and rerun the matching command from the workflow before changing code.

## Install local Git checks

1. **Install the repository hooks once:**

   ```sh
   ./scripts/install-git-hooks.sh
   ```

   The pre-commit hook checks formatting and mobile text/design rules, and runs
   targeted lint or compilation for staged source files. The pre-push hook
   runs broader repository and mobile checks.

   **Check:** a local commit runs the hook without an installation error. If
   a hook is missing, rerun the installer. Hooks do not replace CI; use CI to
   check Xcode and simulator behavior.

Optional: `pnpm test:mutation` runs incremental API mutation tests;
`pnpm test:mutation:full` forces the full configured set. GitHub Actions also
runs the full suite weekly and on manual dispatch. See
[Build Tandem from source](build-from-source.md) to build the apps and API.
