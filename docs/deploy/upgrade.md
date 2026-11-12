# Upgrade or roll back Tandem

For operators updating an installation built from this repository.

## Upgrade

1. **Back up first.** Create and verify a backup using
   [Back up and restore](backup-restore.md).

   **Check:** both backup files exist, are non-empty, and are stored away from
   the deployment host.

2. **Get the new source and build the API.** From the Tandem checkout:

   ```sh
   git pull --ff-only
   docker compose --project-directory "$PWD" \
     --env-file deploy/compose/.env \
     -f deploy/compose/production.yml build api
   ```

   **Check:** the build finishes successfully. If it fails, keep the running
   API and resolve the build error before recreating anything.

3. **Recreate only the API.** Keep PostgreSQL and the Actual workspace volumes.

   ```sh
   docker compose --project-directory "$PWD" \
     --env-file deploy/compose/.env \
     -f deploy/compose/production.yml up -d api
   ```

   **Check:** `docker compose --project-directory "$PWD" --env-file deploy/compose/.env -f deploy/compose/production.yml ps`
   shows the API healthy, and
   `curl --fail https://budget.example.com/api/v1/health/ready` succeeds.

4. **Open the app and sync.** Confirm it lists the expected Actual budgets and
   can complete a small read or write.

   **Check:** the change is visible in Actual. If the API is not ready, see
   [Troubleshooting](troubleshooting.md). Keep the volumes intact while any
   write is uncertain.

## Roll back

Return the checkout to the last tested commit and rebuild/recreate only the API
using steps 2 and 3. Do not use `docker compose down --volumes`. If a release's
notes say its data changes cannot be rolled back, restore the verified backup
instead. Keep the Actual workspace volume until every uncertain write is
settled.
