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
openssl rand -hex 32 > deploy/compose/secrets/auth_token_encryption_key
chmod 444 deploy/compose/secrets/*
unset postgres_password
```

Compose secrets are mounted read-only inside the containers. The `.env` file
contains their paths, not their values; the API reads the database URL and
auth encryption key from those mounted files. Keep the secrets
directory private (`chmod 700 deploy/compose/secrets`). The `0444` file mode
lets the unprivileged API process read the bind-mounted files even when the
deployment host uses a different numeric user ID. Actual account passwords are
entered in Tandem and are not deployment secrets. Do not change the secret
paths in `.env` unless you move these files.

Set `CHANGE_FEED_EPOCH` in `deploy/compose/.env` to a random stable value. Keep
it unchanged for ordinary restarts. Use a new value after restoring a database
backup so mobile devices discard cursors from the pre-restore history. Set
`ACTUAL_SERVER_URL` to the Actual service address. Set `ACTUAL_PUBLIC_URL` to
the public Tandem URL to enable OpenID sign-in. When enabled,
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
and sign-in attempts to 10 per minute. Keep the API origin private so clients
cannot bypass the reverse-proxy limits. The API location compresses JSON
responses of at least 1 KiB when the client supports gzip.

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

In Tandem, enter the public API address and sign in with the Actual account.
Available budgets come from that account's Actual budget catalog.

## Backups and restore

Backups contain PostgreSQL coordination records and the API's Actual workspace.
They may contain transaction data, notes, and decrypted Actual budget files.
By default, `backup.sh` writes plaintext files with mode 600; file permissions
do not encrypt them. Store the output on encrypted storage and keep it off the
deployment host. For end-to-end encryption of the exported files, install GnuPG
from your operating system's package manager and create a passphrase file
outside the repository and backup directory:

```sh
install -d -m 700 /secure/tandem-backup-keys
openssl rand -hex 32 > /secure/tandem-backup-keys/passphrase
chmod 600 /secure/tandem-backup-keys/passphrase
```

Keep that passphrase in a password manager or another protected location,
separate from the backup files. Do not put it in `.env`, the backup directory,
or the same storage account as the backups. Back up `deploy/compose/.env` and
`deploy/compose/secrets/` separately in an approved secrets manager. The `.env`
contains the change-feed epoch and secret file paths; the secret files contain
the database password and auth encryption key.

Run a backup to a protected prefix in a directory accessible only to the
operator; output files are created with mode 600. The API is briefly stopped while its Actual
workspace is archived and is restarted afterward.

```sh
deploy/backup.sh /secure/tandem-backups/tandem-2026-11-01
```

To write encrypted `.gpg` files instead, pass the passphrase-file path to the
backup script. The backup stream is encrypted before it is written to disk:

```sh
TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase \
  deploy/backup.sh /secure/tandem-backups/tandem-2026-11-01-encrypted
```

Restore either a plaintext pair or an encrypted `.gpg` pair during a
maintenance window. Encrypted restore requires the same passphrase file and
decrypts into temporary mode-600 files that the script removes when it exits.
The script validates both files before stopping the API. PostgreSQL restore
runs in a single transaction with errors enabled, so a failed database restore
rolls back its changes. A rejected archive leaves the existing database and
workspace in place and the API running. A successful restore replaces the
current PostgreSQL contents and Actual workspace. Afterward, set a new random
`CHANGE_FEED_EPOCH` in `deploy/compose/.env` before starting the API. The
restore script leaves the API stopped until that explicit step is complete.

```sh
TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase \
  deploy/restore.sh /secure/tandem-backups/tandem-2026-11-01-encrypted \
  new-random-epoch-20261101
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
