# Tandem deployment

Production Compose runs the API and PostgreSQL on a private network. The API
image builds from the pinned Node and pnpm versions, compiles TypeScript in a
build stage, and runs as the unprivileged `node` user in a read-only container.
PostgreSQL is not published to the host. The API is reachable only from the
external Docker network used by the reverse proxy.

## Start the service

Create a dedicated edge network and configure the deployment file with private
credentials:

```sh
docker network create tandem-edge
cp deploy/compose/.env.example deploy/compose/.env
chmod 600 deploy/compose/.env
```

Set `POSTGRES_PASSWORD` to a long random value. `DATABASE_URL` must use the
same database, username, and password; URL-encode any reserved password
characters. Set `CHANGE_FEED_EPOCH` to a random stable value. Keep it unchanged
for ordinary restarts. Use a new value after restoring a database backup so
mobile devices discard cursors from the pre-restore history. Leave
`ACTUAL_SERVER_URL` and `ACTUAL_SERVER_PASSWORD` empty to start without Actual
integration. When enabled, the server release must match
[`docs/actual-compatibility.md`](../docs/actual-compatibility.md).

Attach the existing Nginx container to `tandem-edge`, or set
`TANDEM_EDGE_NETWORK` to its existing external Docker network. Add the two
files under `deploy/nginx/` to the matching Nginx contexts. The API location
must precede the Actual catch-all. Set `TRUST_PROXY` to the exact proxy address
or narrow CIDR observed by the API; do not trust all forwarded headers. Keep
TLS termination and the Actual server's existing proxy settings in the
existing Nginx site configuration.
The example also limits the API to 600 requests per minute per client address
and pairing attempts to 10 per minute. Keep the API origin private so clients
cannot bypass the reverse-proxy limits.

Start and verify:

```sh
docker compose --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml up -d --build
docker compose --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml ps
curl --fail https://budget.example.com/api/v1/health/ready
```

The API has Docker liveness/readiness checks; readiness requires PostgreSQL.
PostgreSQL has its own `pg_isready` check. Compose starts the API after
PostgreSQL is healthy, restarts failed services, gives the API 45 seconds to
shut down, and uses its init process to forward container signals. API startup
runs and validates migrations before listening. Unknown database/driver errors
are logged by error type, without connection strings. Neither the database nor
API ports are published on the host. Allow resource monitoring and persistent
volume monitoring at the Docker host level.

Generate one-time device codes from the API container. Grant the narrowest
budget scope needed; pairing codes are single-use and expire after five
minutes.

```sh
docker compose --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml exec api \
  node dist-cli/bin/pairing-cli.js --budget-id '<Actual budget sync ID>'
```

## Backups and restore

Backups contain PostgreSQL coordination records and the API's Actual workspace.
They may contain transaction data, notes, and decrypted Actual budget files.
Store them encrypted at rest, restrict access, and keep them off the deployment
host. Back up `deploy/compose/.env` separately in an approved secrets manager:
it contains database credentials and the change-feed epoch.

Run a backup to a protected prefix in a directory accessible only to the
operator; output files are created with mode 600. The API is briefly stopped while its Actual
workspace is archived and is restarted afterward.

```sh
deploy/backup.sh /secure/tandem-backups/tandem-2026-11-01
```

Restore both paired files during a maintenance window. The script validates the
PostgreSQL dump and workspace archive before stopping the API. PostgreSQL
restore runs in a single transaction with errors enabled, so a failed database
restore rolls back its changes. A rejected archive leaves the existing database
and workspace in place and the API running. A successful restore replaces the
current PostgreSQL contents and Actual workspace. Afterward, set a new random
`CHANGE_FEED_EPOCH` in `deploy/compose/.env` before starting the API. The
restore script leaves the API stopped until that explicit step is complete.

```sh
deploy/restore.sh /secure/tandem-backups/tandem-2026-11-01 new-random-epoch-20261101
```

Then start and verify the API using the commands above. Confirm that an
authorized client performs a fresh snapshot before returning the deployment
to normal use. Test backups by restoring them to a separate disposable Compose
project regularly; never test restore against a user's only copy.

## Release checks

The CI quality job checks the Nginx snippets with the pinned Nginx image and
builds the production API image. The API test profile uses a separate synthetic
PostgreSQL database, and the Actual compatibility profile uses an isolated
official Actual server with synthetic data. See
[`docs/development.md`](../docs/development.md) for the commands and version
pins.
