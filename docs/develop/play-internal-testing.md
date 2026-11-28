# Google Play internal testing

For maintainers who want the nightly Android build delivered through Google
Play to a short list of testers, instead of installing the APK by hand.

The nightly workflow can upload a signed App Bundle to the Play **internal
testing** track. Only the Google accounts on the track's tester list can see
or install it, and Play delivers updates like any other app. The upload is off
until the repository variable below is set.

## One-time setup in Google Play Console

1. Create a Google Play developer account.
2. Create an app with the package name `app.tandemforactual.android`.
3. Complete the app's **Data safety** form and content rating questionnaire.
4. Turn on **Play App Signing**. The nightly keystore
   (`ANDROID_NIGHTLY_KEYSTORE_BASE64`) acts as the upload key; Play signs what
   it delivers with its own key.
5. Download the `tandem-android-bundle-…` artifact from the latest nightly run
   and upload its `.aab` to **Testing → Internal testing** by hand. Play
   requires the first upload to be manual.
6. Add your Google account to the internal testers list and open the opt-in
   link on your phone.

## Let the nightly upload

1. In Google Cloud, create a service account, then in Play Console give it
   **Release to testing tracks** for this app.
2. Add its JSON key to the repository as the secret
   `PLAY_SERVICE_ACCOUNT_JSON`.
3. Set the repository variable `PLAY_INTERNAL_TESTING` to `true`.

The next nightly builds `androidApp-release.aab` with the same version code as
the nightly APK and uploads it to the internal track.

**Check:** the build appears under **Internal testing → Releases** and the
Play Store offers the update on your phone.

If Play rejects the upload because the app has not been published yet, set the
repository variable `PLAY_RELEASE_STATUS` to `draft` and roll each release out
from Play Console until the first one is live.

## Notes

- Builds from Play and the GitHub APK are signed with different keys, so
  uninstall one before installing the other.
- Personal developer accounts must run a closed test with at least 12 testers
  for 14 days before a production release. Internal testing has no such
  requirement.
