# Upgrade or roll back Tandem

The setup command pins the API image by digest. A later nightly does not
silently change the installed version. Upgrade the API after reviewing and
verifying the new digest; keep PostgreSQL and the Actual workspace volume.

## Upgrade

1. **Back up first.** Create and verify a backup using
   [Back up and restore](backup-restore.md).

   **Check:** both backup files exist, are non-empty, and are stored away from
   the deployment host.

2. **Resolve and verify the current nightly image.** On the Tandem host, run:

   ```sh
   image=ghcr.io/nischay-pro/tandem-api:nightly
   digest="sha256:$(docker buildx imagetools inspect --raw "$image" | sha256sum | cut -d ' ' -f 1)"
   pinned_image="ghcr.io/nischay-pro/tandem-api@$digest"
   gh attestation verify "oci://$pinned_image" --repo Nischay-Pro/tandem
   docker pull "$pinned_image"
   ```

   **Check:** verification succeeds and Docker pulls the image. If either
   command fails, keep the running API and resolve the registry or attestation
   problem before changing the installed version.

3. **Save the current image reference, then update the API image.** Replace
   `/opt/tandem` with the install directory if needed:

   ```sh
   environment_file=/opt/tandem/deploy/compose/.env
   current_image="$(sed -n 's/^TANDEM_API_IMAGE=//p' "$environment_file")"
   printf '%s\n' "$current_image" > /secure/tandem-previous-api-image
   chmod 600 /secure/tandem-previous-api-image
   sudo sed -i "s|^TANDEM_API_IMAGE=.*$|TANDEM_API_IMAGE=$pinned_image|" "$environment_file"
   docker compose --project-directory /opt/tandem \
     --env-file "$environment_file" \
     -f /opt/tandem/deploy/compose/production.yml pull api
   docker compose --project-directory /opt/tandem \
     --env-file "$environment_file" \
     -f /opt/tandem/deploy/compose/production.yml up -d api
   ```

   **Check:** the API reports healthy:

   ```sh
   docker compose --project-directory /opt/tandem \
     --env-file /opt/tandem/deploy/compose/.env \
     -f /opt/tandem/deploy/compose/production.yml ps
   curl --fail https://budget.example.com/api/v1/health/ready
   ```

   If it is not healthy, keep the volumes intact and follow the rollback steps.

4. **Open the app and sync.** Confirm it lists the expected Actual budgets and
   can complete a small read or write.

   **Check:** the change is visible in Actual. If the API is not ready, see
   [Troubleshooting](troubleshooting.md). Keep the volumes intact while any
   write is uncertain.

## Roll back

Restore the prior digest from `/secure/tandem-previous-api-image` in
`TANDEM_API_IMAGE`, then pull and recreate only the API:

```sh
previous_image="$(cat /secure/tandem-previous-api-image)"
sudo sed -i "s|^TANDEM_API_IMAGE=.*$|TANDEM_API_IMAGE=$previous_image|" \
  /opt/tandem/deploy/compose/.env
docker compose --project-directory /opt/tandem \
  --env-file /opt/tandem/deploy/compose/.env \
  -f /opt/tandem/deploy/compose/production.yml pull api
docker compose --project-directory /opt/tandem \
  --env-file /opt/tandem/deploy/compose/.env \
  -f /opt/tandem/deploy/compose/production.yml up -d api
```

Do not use `docker compose down --volumes`. If a release's notes say its data
changes cannot be rolled back, restore the verified backup instead. Keep the
Actual workspace volume until every uncertain write is settled.
