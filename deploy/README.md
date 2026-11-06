# Tandem deployment

Production Compose runs the API and PostgreSQL on a private network. The API
image builds from the pinned Node and pnpm versions, compiles TypeScript in a
build stage, and runs as the unprivileged `node` user in a read-only container.
PostgreSQL is not published to the host. The API is reachable only from the
external Docker network used by the reverse proxy.

## Start the service

Create a dedicated edge network, a private directory for Compose secrets, and
the deployment environment file:

```sh
docker network create tandem-edge
cp deploy/compose/.env.example deploy/compose/.env
chmod 600 deploy/compose/.env
mkdir -m 700 -p deploy/compose/secrets
postgres_password="$(openssl rand -hex 32)"
printf '%s' "$postgres_password" > deploy/compose/secrets/postgres_password
printf 'postgresql://tandem:%s@postgres:5432/tandem' "$postgres_password" > deploy/compose/secrets/database_url
openssl rand -hex 32 > deploy/compose/secrets/pairing_code_hash_key
: > deploy/compose/secrets/actual_server_password
chmod 444 deploy/compose/secrets/*
unset postgres_password
```

Compose secrets are mounted read-only inside the containers. The `.env` file
contains their paths, not their values; the API reads the database URL, pairing
key, and optional Actual password from those mounted files. Keep the secrets
directory private (`chmod 700 deploy/compose/secrets`). The `0444` file mode
lets the unprivileged API process read the bind-mounted files even when the
deployment host uses a different numeric user ID. If Actual requires a
password, write it to `actual_server_password` without a trailing newline and
restore that file's mode to `0444`; leave it empty when Actual integration is
disabled. Do not change the secret paths in `.env` unless you move these files.

Set `CHANGE_FEED_EPOCH` in `deploy/compose/.env` to a random stable value. Keep
it unchanged for ordinary restarts. Use a new value after restoring a database
backup so mobile devices discard cursors from the pre-restore history. Leave
`ACTUAL_SERVER_URL` empty to start without Actual integration. When enabled,
the server release must match
[`docs/actual-compatibility.md`](../docs/actual-compatibility.md).

When moving an existing deployment to this Compose file, populate the secret
files with the credentials already in use. In particular, the Postgres
password secret and database URL must match the existing database role; setting
`POSTGRES_PASSWORD_FILE` does not rotate a password in an initialized volume.
The existing PostgreSQL and Actual workspace volumes are preserved.

The production Postgres and Node images are pinned by multi-platform registry
digests. Review and update those digests together with their version tags when
adopting a new image release.

Attach the existing Nginx container to `tandem-edge`, or set
`TANDEM_EDGE_NETWORK` to its existing external Docker network. Add the two
files under `deploy/nginx/` to the matching Nginx contexts. The API location
must precede the Actual catch-all. Set the required `TRUST_PROXY` value to the
exact proxy address or narrow CIDR observed by the API; production Compose
refuses an empty value. Do not trust all forwarded headers. Keep
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
host. Back up `deploy/compose/.env` and `deploy/compose/secrets/` separately in
an approved secrets manager. The `.env` contains the change-feed epoch and
secret file paths; the secret files contain the database and pairing
credentials.

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
