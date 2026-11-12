# Tandem deployment

Production Compose runs the API and PostgreSQL on a private network. The API
image builds from the pinned Node and pnpm versions, compiles TypeScript in a
build stage, and runs as the unprivileged `node` user in a read-only container.
PostgreSQL is not published to the host. The API is reachable only from the
external Docker network used by the reverse proxy.

Run one API process. Snapshot paging state is held in a bounded in-memory cache;
multiple replicas would not share a page generation and could repeatedly force
mobile clients to restart their snapshot download.

See the [configuration reference](configuration.md),
[reverse-proxy guide](reverse-proxy.md), and
[troubleshooting guide](troubleshooting.md) for the next steps.

## Start the service

Create a dedicated edge network, a private directory for Compose secrets, and
the deployment environment file:

```sh
docker network create tandem-edge
cp deploy/compose/.env.example deploy/compose/.env
chmod 600 deploy/compose/.env
mkdir -p deploy/compose/secrets
chmod 700 deploy/compose/secrets
postgres_password="$(openssl rand -hex 32)"
printf '%s' "$postgres_password" > deploy/compose/secrets/postgres_password
printf 'postgresql://tandem:%s@postgres:5432/tandem' "$postgres_password" > deploy/compose/secrets/database_url
openssl rand -hex 32 > deploy/compose/secrets/auth_token_encryption_key
touch deploy/compose/secrets/auth_token_encryption_key_previous
touch deploy/compose/secrets/actual_session_token
sudo chown root:1000 deploy/compose/secrets/*
sudo chmod 0440 deploy/compose/secrets/*
unset postgres_password
```

Compose secrets are mounted read-only inside the containers. The `.env` file
contains their paths, not their values; the API reads the database URL, Actual
session token, and auth encryption key from those mounted files. The Actual
session-token file is empty by default, which leaves normal per-person sign-in
unchanged. The API runs as UID/GID 1000 in
the supplied image. On a Linux host using rootful Docker, the files are owned by
`root:1000` and use mode `0440`: the API can read them through its group, while
other host users cannot read them through file permissions. Keep the secrets
directory private (`chmod 700 deploy/compose/secrets`) as an additional layer.
If you change the API's runtime GID, use that GID instead of `1000`. Actual
account passwords are entered in Tandem and are not deployment secrets. Do not
change the secret paths in `.env` unless you move these files.

### Optional operator-managed Actual session

Most deployments should leave `actual_session_token` empty. Tandem users sign
in through Actual, and Tandem keeps each user's session token encrypted in
PostgreSQL. An operator-managed token is only needed when the API itself must
start an Actual session before anyone signs in. It is not sent to the app.

To obtain one, sign in to the Actual web app in a browser. Open that browser's
developer tools and find IndexedDB database `actual`, object store
`asyncStorage`, key `user-token`. Copy the value into
`deploy/compose/secrets/actual_session_token`; do not put it in `.env`, shell
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
sudo install -o root -g 1000 -m 0440 /secure/new-auth-key deploy/compose/secrets/auth_token_encryption_key
sudo install -o root -g 1000 -m 0440 /secure/old-auth-key deploy/compose/secrets/auth_token_encryption_key_previous
```

Restart the API and wait for it to become healthy; startup re-encrypts every
stored Actual session in one transaction and fails without changing the rows if
any cannot be decrypted. Then replace the previous-key file with an empty file
using the same owner and mode, then restart the API and confirm it becomes
healthy again. To clear the previous-key file, install an empty file with the
same ownership and permissions:

```sh
sudo install -o root -g 1000 -m 0440 /dev/null deploy/compose/secrets/auth_token_encryption_key_previous
```

Keep the old key in protected backup storage while any database backup may still
contain tokens encrypted with it.

Set `ACTUAL_SERVER_URL` to the Actual service address. Set `ACTUAL_PUBLIC_URL` to
the public Tandem URL to enable OpenID sign-in. When enabled,
the server release must match
[Actual compatibility](../concepts/actual-compatibility.md).

When moving an existing deployment to this Compose file, populate the secret
files with the credentials already in use. In particular, the Postgres
password secret and database URL must match the existing database role; setting
`POSTGRES_PASSWORD_FILE` does not rotate a password in an initialized volume.
Create the new `auth_token_encryption_key_previous` secret file as zero bytes
unless you are rotating keys, and keep its path in `.env`. Apply the
`root:1000` ownership and `0440` mode before starting the API. The existing
PostgreSQL and Actual workspace volumes are preserved.
Keep the `actual-workspace` named volume while any write operation is
uncertain. It contains Actual's local budget data, including writes that may
not yet have reached the budget file; deleting it can discard those changes.

The production Postgres and Node images are pinned by multi-platform registry
digests. Review and update those digests together with their version tags when
adopting a new image release.

Configure the TLS reverse proxy using the [reverse-proxy guide](reverse-proxy.md). Set `TRUST_PROXY` to the proxy address the API sees.

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

The response should be `{"status":"ok"}` as JSON. If this URL returns the
Actual web page, the reverse proxy is sending `/api/v1/` to Actual instead of
the Tandem API. Add the API locations from
[`deploy/nginx/tandem-locations.conf.example`](../../deploy/nginx/tandem-locations.conf.example)
to the same TLS server block as Actual.

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
docker compose --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml exec postgres df -h /var/lib/postgresql
docker compose --project-directory "$PWD" \
  --env-file deploy/compose/.env \
  -f deploy/compose/production.yml exec api df -h /data/actual-api
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
