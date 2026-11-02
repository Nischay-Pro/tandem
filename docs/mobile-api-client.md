# Generated Kotlin API client

The mobile API client is generated from the checked-in
[`docs/api/openapi-v1.json`](api/openapi-v1.json) contract. The generator runs
as part of Gradle compilation; generated sources stay under `mobile/api-client/build`
and are not committed. The OpenAPI drift check remains the source-of-truth gate
for route changes.

The module also contains `TandemApiClient` for one-time device pairing and the
authorized Actual budget catalog, plus `GeneratedBudgetSyncRemote`, the
handwritten adapter between generated endpoint calls and the shared data
module's sync protocol. The Compose app calls these APIs to pair a device, list
its authorized budgets, perform the initial local sync, and display recent
transactions from the local database. The persistence and sync lifecycle are
documented in [`docs/mobile-sync.md`](mobile-sync.md).

The module targets Android (minSdk 26, compileSdk 36) and iOS (device ARM64 and
Apple Silicon simulator). The pinned generator's stable release does
not publish an Intel iOS simulator variant, so the client does not declare that
target. iOS framework compilation and Xcode integration run on macOS; a Linux
build cannot validate Apple toolchains.

## Pinned toolchain

- Kotlin Multiplatform Gradle plugin: 2.4.21 (stable)
- Android Gradle plugin: 9.3.1 (latest version fully supported by Kotlin 2.4.21)
- Gradle wrapper: 9.8.2 (current stable release; Kotlin 2.4.21's documented fully
  supported Gradle range ends at 9.7.0, so CI validates this newer combination)
- OpenAPI KMP Gen: 1.5.0 (latest stable release; 1.6 is a release candidate)
- ktlint Gradle plugin: 14.2.0
- Kover: 0.9.11
- JVM runtime: Java 27 (current stable runtime; Gradle 9.8.2 supports running on it)
- Android compile SDK: 36, minSdk: 26

Kotlin 2.4.21 currently warns that JVM target 27 is unsupported and falls back
to JVM 26 bytecode while the build runs on Java 27. Revisit that target when a
Kotlin release adds JVM 27 target support.

Versions are exact in `gradle/libs.versions.toml` and the Gradle wrapper. Update
them as one tested toolchain change, and keep release candidates out of CI.

## Build and lint

From `mobile/`:

```sh
./gradlew :data:jvmTest :data:koverVerify :data:ktlintCheck \
  :api-client:compileAndroidMain :api-client:compileCommonMainKotlinMetadata \
  :api-client:jvmTest :api-client:koverVerify :api-client:ktlintCheck
```

This verifies parsing the OpenAPI 3.1 contract, generated client code, Android,
JVM behavior, and lint. The app targets and Android host can be compiled with:

```sh
./gradlew :composeApp:compileAndroidMain :composeApp:ktlintCheck :androidApp:assembleDebug
```

CI also compiles both iOS ARM64 targets on macOS with Xcode installed. To run
those tasks locally, use:

```sh
./gradlew :api-client:compileKotlinIosArm64 :api-client:compileKotlinIosSimulatorArm64
```

Kover enforces 100% JVM line coverage for the generated API operations, auth
adapter, and API holder. Its report treats the generated `@SerialName("status")`
annotation on the readiness model as an uncovered executable line, although
that annotation is compile-time serialization metadata. Kover therefore filters
that generated model and its nested classes from the line denominator; the JVM
test still decodes the readiness response, reads its status, and asserts the
serialized JSON field name and value. Kover currently collects KMP coverage only
from common and JVM source sets; it does not report iOS/native test coverage.

Do not edit generated files. Change Fastify schemas, regenerate the OpenAPI
contract, then rebuild the client. Review generated API/model changes as part of
the contract update.
