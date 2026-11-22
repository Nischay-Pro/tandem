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
   On Linux or WSL, run `sha256sum`.

   ```sh
   sha256sum -c Tandem-0.1.0-nightly.VERSION-COMMIT.apk.sha256
   ```

   On macOS, run `shasum`.

   ```sh
   shasum -a 256 -c Tandem-0.1.0-nightly.VERSION-COMMIT.apk.sha256
   ```

   Replace `VERSION-COMMIT` with the version and commit shown in the release.
   **Check:** the command prints `OK`. If it reports a mismatch, delete both
   downloads and get them again from the same release.

3. **Install the APK.** Open the verified APK file. Android may ask you to
   allow installation from that source.

   **Check:** Tandem appears in the app list and opens. If Android blocks the
   installation, use its settings prompt for that file source, then reopen the
   APK.

4. **Connect to your budget.** Enter your Actual server's HTTPS address, sign
   in with your Actual password and choose a budget. Encrypted budgets ask for
   their budget password.

   **Check:** Tandem reports that the first sync is complete. If sign-in fails,
   open the same address in a browser and confirm Actual loads.

5. **Choose how Tandem syncs.** After the first sync and any notification
   prompt, choose whether Tandem should sync in the background. Under **Sync
   over**, choose **Wi-Fi**, **Mobile**, or **Both** for automatic sync. Tandem
   treats hotspots and metered Wi-Fi as mobile data. **Sync now** is available
   on any connection. You can change these choices in Settings.

## Use a local server address

If your operator provides a separate address for your home network or VPN,
open **Settings → Server and device → Local address**. Enter the address and
tap **Save and check**. Tandem uses it after verifying the server's identity.
It falls back to your main address when the local address is unavailable.

## Install on iPhone

Tandem does not currently have an App Store or TestFlight build. Developers can
build the iOS app from the [source instructions](../develop/build-from-source.md).
