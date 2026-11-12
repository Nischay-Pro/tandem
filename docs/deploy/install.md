# Tandem deployment

Production Compose runs the API and PostgreSQL on a private network. The setup
command selects a nightly API image by digest and creates the protected secret
files. PostgreSQL is not published to the host. The API is reachable only from
the external Docker network used by the reverse proxy.

Run one API process. Snapshot paging state is held in a bounded in-memory cache;
multiple replicas would not share a page generation and could repeatedly force
mobile clients to restart their snapshot download.

See the [configuration reference](configuration.md),
[reverse-proxy guide](reverse-proxy.md), and
[troubleshooting guide](troubleshooting.md) for the next steps.

## Start a new deployment

Before starting, install Docker Engine with Compose v2 and Buildx, plus `curl`,
`openssl`, and `sudo`. Use a Linux host with rootful Docker so the API can read
the secret files as its unprivileged container user.

1. Download and run the setup command from the directory where you want the
   deployment files:

```sh
curl --fail --silent --show-error --location \
  https://raw.githubusercontent.com/Nischay-Pro/tandem/main/deploy/tandem-setup.sh \
  --output tandem-setup.sh
bash tandem-setup.sh
```

The command downloads the production Compose file, resolves the public nightly
image to a digest, creates the `tandem-edge` network if needed, and asks for
the Actual URL, public Tandem URL and reverse proxy IP/CIDR. It writes files to
`./tandem` by default. It also downloads the backup, restore, systemd and proxy
examples into `tandem/deploy/`. Settings and secrets are stored in
`tandem/deploy/compose/`. The command never replaces an existing `.env` file or
secret. Running it again leaves an existing installation unchanged.

Check that the API image is pinned and the secret files exist:

```sh
grep '^TANDEM_API_IMAGE=.*@sha256:' tandem/deploy/compose/.env
test -s tandem/deploy/compose/secrets/postgres_password
test -s tandem/deploy/compose/secrets/database_url
test -s tandem/deploy/compose/secrets/auth_token_encryption_key
```

2. Start the services and check their health:

```sh
docker compose --project-directory "$PWD/tandem" \
  --env-file "$PWD/tandem/deploy/compose/.env" \
  -f "$PWD/tandem/deploy/compose/production.yml" up -d
docker compose --project-directory "$PWD/tandem" \
  --env-file "$PWD/tandem/deploy/compose/.env" \
  -f "$PWD/tandem/deploy/compose/production.yml" ps
```

The `api` and `postgres` services should report healthy. If setup cannot read
the nightly image without credentials, check the network and the image
package's public visibility. If Compose reports a missing image setting, rerun
setup from the same directory or restore its `.env` file.

3. Configure TLS using the [reverse-proxy guide](reverse-proxy.md), then check
   readiness:

```sh
curl --fail https://budget.example.com/api/v1/health/ready
```

The response should be `{"status":"ok"}` as JSON. If this URL returns the
Actual web page, the reverse proxy is sending `/api/v1/` to Actual instead of
the Tandem API. Add the API locations from
[`deploy/nginx/tandem-locations.conf.example`](../../deploy/nginx/tandem-locations.conf.example)
to the same TLS server block as Actual.

### If Actual already runs in Compose

Keep the generated Tandem Compose file as a separate stack. Attach the Actual
service and reverse proxy to the external `tandem-edge` network, then set
`ACTUAL_SERVER_URL` to the Actual service name and port if the API needs an
operator-managed Actual session. This reuses the production Compose file and
avoids duplicating its API or PostgreSQL definitions.

The downloaded Nginx examples are under `tandem/deploy/nginx/`.

Compose secrets are mounted read-only inside the containers. The `.env` file
contains file paths, not secret values. The Actual session-token file is empty
by default, which leaves per-person sign-in unchanged. The API runs as
UID/GID 1000. On a Linux host using rootful Docker, setup creates secret files
owned by `root:1000` with mode `0440` and keeps the secret directory private.
Actual account passwords are entered in Tandem and are not deployment secrets.

### Optional operator-managed Actual session

Most deployments should leave `actual_session_token` empty. Tandem users sign
in through Actual, and Tandem keeps each user's session token encrypted in
PostgreSQL. An operator-managed token is only needed when the API itself must
start an Actual session before anyone signs in. It is not sent to the app.

To obtain one, sign in to the Actual web app in a browser. Open that browser's
developer tools and find IndexedDB database `actual`, object store
`asyncStorage`, key `user-token`. Copy the value into
`tandem/deploy/compose/secrets/actual_session_token`; do not put it in `.env`, shell
history, a command-line argument, or a tracked file. Keep the existing
`root:1000` ownership and `0440` mode, then restart the API. The token grants
the same budget access as that Actual account. If it expires or is revoked,
Tandem reports it in readiness and the API log; replace the secret and restart
the API. Tandem does not refresh operator-managed tokens automatically.

The auth encryption key must remain stable between rotations. Keep a protected
copy with the database backups. The always-mounted
`auth_token_encryption_key_previous` file should be zero bytes during normal
operation. To rotate keys, generate a new 32-byte key and replace the current
and previous files while preserving their ownership and mode. For example:

```sh
sudo install -o root -g 1000 -m 0440 /secure/new-auth-key tandem/deploy/compose/secrets/auth_token_encryption_key
sudo install -o root -g 1000 -m 0440 /secure/old-auth-key tandem/deploy/compose/secrets/auth_token_encryption_key_previous
```

Restart the API and wait for it to become healthy; startup re-encrypts every
stored Actual session in one transaction and fails without changing the rows if
any cannot be decrypted. Then replace the previous-key file with an empty file
using the same owner and mode, then restart the API and confirm it becomes
healthy again. To clear the previous-key file, install an empty file with the
same ownership and permissions:

```sh
sudo install -o root -g 1000 -m 0440 /dev/null tandem/deploy/compose/secrets/auth_token_encryption_key_previous
```

Keep the old key in protected backup storage while any database backup may still
contain tokens encrypted with it.

Set `ACTUAL_SERVER_URL` to the Actual service address. Set `ACTUAL_PUBLIC_URL` to
the public Tandem URL to enable OpenID sign-in. When enabled, the server release must match
[Actual compatibility](../concepts/actual-compatibility.md).

When updating an existing deployment, do not run first-install setup against
its data directory. Preserve its `.env`, secret files, Compose project name and
named volumes. The Postgres password secret and database URL must match the
existing database role; changing `POSTGRES_PASSWORD_FILE` does not rotate a
password in an initialized volume. Set `TANDEM_API_IMAGE` to the new
digest-pinned image. Keep the `actual-workspace` volume while any write
operation is uncertain; it can contain Actual data that has not reached the
budget file yet.

Setup resolves the current nightly API image once and records its digest in
`deploy/compose/.env`, so a later nightly does not silently replace the
installed image. To update, select and verify a new digest, edit
`TANDEM_API_IMAGE`, then run Compose `up -d`. An empty `TRUST_PROXY` is the safe default: forwarded
client-address headers are ignored. Set it to the reverse proxy's IP or narrow
CIDR if rate limits should use the client address.

The API has Docker liveness/readiness checks; readiness verifies PostgreSQL can
write to its volume and checks free space on the Actual workspace volume.
PostgreSQL has its own `pg_isready` check. Compose starts the API after
PostgreSQL is healthy, restarts failed services, gives the API 45 seconds to
shut down, and uses its init process to forward container signals. API startup
runs and validates migrations before listening. Unknown database/driver errors
and unexpected request exceptions are logged by error type only; messages,
stack traces, and connection strings are omitted. Neither the database nor API
ports are published on the host. Allow resource monitoring and persistent
volume monitoring at the Docker host level.

If readiness or a write returns `server_storage_full`, Tandem has stopped
accepting writes until the server has free space. Check both named volumes:

```sh
docker compose --project-directory "$PWD/tandem" \
  --env-file "$PWD/tandem/deploy/compose/.env" \
  -f "$PWD/tandem/deploy/compose/production.yml" exec postgres df -h /var/lib/postgresql
docker compose --project-directory "$PWD/tandem" \
  --env-file "$PWD/tandem/deploy/compose/.env" \
  -f "$PWD/tandem/deploy/compose/production.yml" exec api df -h /data/actual-api
```

Free space on the host or expand the volume, then check `/api/v1/health/ready`
again; readiness recovers after PostgreSQL can write and the Actual workspace
has free blocks. Do not remove `postgres-data` or `actual-workspace` to recover:
they contain the operation journal and local Actual budget files.

In Tandem, enter the public API address and sign in with the Actual account.
Available budgets come from that account's Actual budget catalog.

For remote sign-in, use HTTPS. HTTP does not encrypt the Actual password or
session credentials in transit; use it only on a trusted local network. The
Actual OpenID callback URL carries a short-lived Actual session token in its
query string, so this example disables access logging and suppresses
non-emergency error logs for that callback path. Custom reverse proxies must
also omit its query string from access and error logs.

## Back up and restore

See the [backup and restore guide](backup-restore.md) for manual and scheduled
backups, encrypted archives, restore safety copies, and recovery checks.

For build and release validation, see [Build and test](../develop/build-and-test.md).
