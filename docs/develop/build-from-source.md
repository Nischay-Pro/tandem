# Build Tandem from source

For contributors building the API or mobile app. The API uses Docker for its
local PostgreSQL service. Android builds need the Android SDK; iOS app builds
need macOS and Xcode.

## What you need

- The versions listed in the [toolchain reference](toolchain.md).
- Git, Docker Compose v2, Node.js, and pnpm for repository tasks.
- Java and the Android SDK for Android builds.
- macOS with Xcode for the iOS app target.

## Get the source and dependencies

1. **Clone the repository:**

   ```sh
   git clone https://github.com/Nischay-Pro/tandem.git
   cd tandem
   ```

   **Check:** `README.md` and `package.json` are in the current directory. If
   cloning fails, confirm Git can reach GitHub and retry.

2. **Install the pinned JavaScript dependencies:**

   ```sh
   pnpm install --frozen-lockfile
   ```

   **Check:** `node --version` and `pnpm --version` match the pinned versions.
   If they do not, install the versions in `mise.toml` and retry.

## Build the API

1. **Build and start the local API and PostgreSQL service:**

   ```sh
   docker compose -f docker-compose.dev.yml up --build api
   ```

   **Check:** in another terminal, request the API contract:

   ```sh
   curl --fail http://localhost:19753/api/v1/openapi.json
   ```

   If the API does not start, inspect the Compose output and use the
   [server troubleshooting guide](../deploy/troubleshooting.md). To use Actual
   routes, set `ACTUAL_SERVER_URL` to the address reachable from the API
   container; set `ACTUAL_PUBLIC_URL` only when testing Actual OpenID sign-in.

## Build the Android app

1. **Build the debug APK** from the `mobile/` directory:

   ```sh
   ./gradlew :androidApp:assembleDebug
   ```

   **Check:** Gradle reports success and creates
   `androidApp/build/outputs/apk/debug/androidApp-debug.apk`. If the Android
   SDK is not found, set `ANDROID_HOME` to its installation path; see the
   [toolchain reference](toolchain.md).

2. **Install it on a connected Android device** if you need to inspect the UI:

   ```sh
   adb devices -l
   adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   ```

   **Check:** `adb devices -l` lists the intended device and installation
   completes. If ADB is unavailable, install Android SDK Platform-Tools and
   reconnect the device.

## Build the iOS app

1. **Build the simulator app** on a Mac with Xcode selected:

   ```sh
   xcodebuild \
     -project iosApp/TandemForActual.xcodeproj \
     -scheme TandemForActual \
     -destination 'generic/platform=iOS Simulator' \
     ARCHS=arm64 ONLY_ACTIVE_ARCH=YES \
     CODE_SIGNING_ALLOWED=NO \
     build
   ```

   Run from `mobile/`. The Xcode build phase builds and links the shared
   Compose framework. **Check:** Xcode ends with `BUILD SUCCEEDED`. If it cannot
   find Xcode or the simulator SDK, select a full Xcode installation with
   `xcode-select` and open the project once in Xcode.

For tests, use [Testing and CI](testing.md). For a verified test of the pinned
Actual release, use [Actual compatibility tests](actual-compatibility-tests.md).
