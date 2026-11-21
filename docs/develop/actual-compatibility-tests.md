# Actual compatibility tests

`scripts/test-actual-compatibility.sh` checks Tandem's direct sync against the
pinned Actual server release:

1. Starts the pinned Actual image and bootstraps a password.
2. Seeds envelope and tracking budgets, then creates an end-to-end encrypted
   budget, all with Actual's own client.
3. Runs the Kotlin round trips: every budget edit applied through Actual and read
   back by Actual's client, and an encrypted budget opened, edited and read back.

CI runs it in the API client job. Run it locally with Docker and the Android SDK:

```sh
./scripts/test-actual-compatibility.sh
```

When Actual releases a new version, update the image digest in the script and
the fixtures, then rerun.
