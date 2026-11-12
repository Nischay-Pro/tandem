# Install the Tandem app

For people installing Tandem on an Android phone. Android builds are nightly
prereleases. An iOS build is available from source only.

## Install on Android

1. **Download the APK and its checksum** from the
   [latest nightly release](https://github.com/Nischay-Pro/tandem/releases/tag/nightly).
   Download the `.apk` and matching `.apk.sha256` file from the same release.

   **Check:** both file names contain the same nightly version and source
   commit. If either file is missing, wait for the next complete nightly
   release.

2. **Verify the downloaded APK** in the directory where you saved both files.
   The command below works in a Linux, macOS, or WSL terminal:

   ```sh
   sha256sum -c Tandem-0.1.0-nightly.VERSION-COMMIT.apk.sha256
   ```

   Replace `VERSION-COMMIT` with the version and commit shown in the release.
   **Check:** `sha256sum` prints `OK`. If it reports a mismatch, delete both
   downloads and get them again from the same release.

3. **Install the APK.** Open the verified APK file. Android may ask you to
   allow installation from that source.

   **Check:** Tandem appears in the app list and opens. If Android blocks the
   installation, use its settings prompt for that file source, then reopen the
   APK.

4. **Connect to your budget.** Enter the Actual server address provided by the
   operator, then sign in with your Actual account and choose a budget. Tandem
   checks for its API Gateway at `/api/v1/`; use the server address without
   adding that path.

   **Check:** the budget opens after its first sync. If sign-in fails, confirm
   the operator has installed Tandem's API Gateway and that the server address
   reaches it. See [server troubleshooting](../deploy/troubleshooting.md).

## Install on iPhone

Tandem does not currently have an App Store or TestFlight build. Developers can
build the iOS app from the [source instructions](../develop/build-from-source.md).
