# Install Tandem

For operators adding Tandem beside an existing Actual Budget server. This guide
installs one Tandem API and its PostgreSQL database with the latest published
nightly Compose bundle. The setup command stops without changing a deployment if
that bundle is unavailable.

## What you need

- A Linux host with rootful Docker Engine and Docker Compose v2.
- `curl`, `docker`, `jq`, `openssl`, `sha256sum`, `sudo`, and `tar`.
- An Actual server reachable from the Tandem host.
- A domain and HTTPS reverse proxy for remote phone connections.

## Install and start

1. **Download and run the setup command** from the directory where you want the
   installation files:

   ```sh
   curl --fail --silent --show-error --location \
     https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy/tandem-setup.sh \
     --output tandem-setup.sh
   bash tandem-setup.sh
   ```

   The command downloads the latest nightly Compose bundle, verifies its
   SHA-256 checksum, asks for the Actual URL, public Tandem URL, and proxy
   address, then creates `./tandem/`. It writes generated secret files under
   `tandem/deploy/compose/secrets/`; `.env` contains settings and secret-file
   paths, not secret values. The command does not replace an existing `.env`,
   secret, or Compose file.

   **Check:** the pinned image and required secret files are present:

   ```sh
   grep -Eq '^[[:space:]]+image: ghcr.io/nischay-pro/tandem-api@sha256:[a-f0-9]{64}$' \
     tandem/deploy/compose/production.yml
   test -s tandem/deploy/compose/secrets/postgres_password
   test -s tandem/deploy/compose/secrets/database_url
   test -s tandem/deploy/compose/secrets/auth_token_encryption_key
   ```

   If the command reports that no Compose bundle is available, leave any
   existing deployment running and retry after the nightly release is
   published. If a check fails, do not start Compose; see
   [Troubleshooting](troubleshooting.md).

2. **If Actual already runs in Compose, include Tandem in that project.** From
   the directory containing the existing Compose file, add:

   ```yaml
   include:
     - path: ./tandem/deploy/compose/production.yml
       project_directory: ./tandem
       env_file: ./tandem/deploy/compose/.env
   ```

   Replace `./tandem` with the directory created by setup. Add the external
   `tandem-edge` network to the existing Actual and reverse-proxy services,
   preserving their other networks:

   ```yaml
   services:
     actual:
       networks:
         - default
         - tandem-edge
     nginx:
       networks:
         - default
         - tandem-edge

   networks:
     tandem-edge:
       name: tandem-edge
       external: true
   ```

   Use the service names from your Compose file. In
   `tandem/deploy/compose/.env`, set `TANDEM_ACTUAL_SERVER_URL` to the Actual
   service address reachable on `tandem-edge`, for example
   `http://actual:5006`. Keep `TANDEM_ACTUAL_PUBLIC_URL` set to the public HTTPS
   Tandem address if people sign in through Actual OpenID. Setup creates the
   `tandem-edge` network.

   **Check:** render the merged project and confirm it includes the existing
   Actual service plus `tandem-api` and `tandem-postgres`:

   ```sh
   docker compose -f compose.yml config --services
   ```

   If Compose cannot resolve `include.path` or the services do not appear,
   check the setup directory and `env_file` path. Compose `include` requires
   version 2.20.3 or newer. If the proxy cannot reach the API, follow
   [Reverse proxy setup](reverse-proxy.md).

3. **Start Tandem and check health.** For a standalone installation, run:

   ```sh
   docker compose --project-directory "$PWD/tandem" \
     --env-file "$PWD/tandem/deploy/compose/.env" \
     -f "$PWD/tandem/deploy/compose/production.yml" up -d
   docker compose --project-directory "$PWD/tandem" \
     --env-file "$PWD/tandem/deploy/compose/.env" \
     -f "$PWD/tandem/deploy/compose/production.yml" ps
   ```

   If you use Compose `include`, run `up -d` from the existing project instead.
   The included API joins the external `tandem-edge` network automatically.

   **Check:** `tandem-api` and `tandem-postgres` report `healthy`. If not, read
   their logs and use [Troubleshooting](troubleshooting.md). Do not delete the
   `tandem-postgres-data` or `tandem-actual-workspace` volumes; they hold the
   Tandem database and Actual working copy.

4. **Route the API through HTTPS.** Add the Tandem locations to the same TLS
   server block as Actual. Follow [Reverse proxy setup](reverse-proxy.md) and
   keep `/api/v1/` routed to Tandem before Actual's catch-all.

   **Check:** from a device that can reach the public address, run:

   ```sh
   curl --fail https://budget.example.com/api/v1/health/ready
   ```

   The response should be `{"status":"ok"}`. If Actual's website appears,
   the proxy sent `/api/v1/` to the wrong service. If readiness fails, keep the
   volumes and follow [Troubleshooting](troubleshooting.md).

5. **Sign in and sync a budget.** Enter the public Tandem address in the app,
   sign in with the Actual account, choose a budget, and complete its first
   sync.

   **Check:** the budget opens and its saved transactions appear. If sign-in
   fails while Actual's website works, compare the Actual service address with
   [Actual compatibility](../concepts/actual-compatibility.md).

## Where to go next

- Change environment settings with the [configuration reference](configuration.md).
- Set up backups before relying on the service:
  [Back up and restore](backup-restore.md).
- Review the [trust and security guide](trust.md).
- Upgrade a running installation with the [upgrade guide](upgrade.md). Do not
  run first-install setup against its existing data directory.

The installed Compose file is pinned to the exact API image digest in its
nightly release bundle; it does not update automatically. Keep the Actual
workspace volume while any write status is uncertain, because it can contain
changes not yet synced to the Actual budget file.
