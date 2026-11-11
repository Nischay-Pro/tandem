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
mkdir -p deploy/compose/secrets
chmod 700 deploy/compose/secrets
postgres_password="$(openssl rand -hex 32)"
printf '%s' "$postgres_password" > deploy/compose/secrets/postgres_password
printf 'postgresql://tandem:%s@postgres:5432/tandem' "$postgres_password" > deploy/compose/secrets/database_url
openssl rand -hex 32 > deploy/compose/secrets/auth_token_encryption_key
touch deploy/compose/secrets/auth_token_encryption_key_previous
sudo chown root:1000 deploy/compose/secrets/*
sudo chmod 0440 deploy/compose/secrets/*
unset postgres_password
```

Compose secrets are mounted read-only inside the containers. The `.env` file
contains their paths, not their values; the API reads the database URL and
auth encryption key from those mounted files. The API runs as UID/GID 1000 in
the supplied image. On a Linux host using rootful Docker, the files are owned by
`root:1000` and use mode `0440`: the API can read them through its group, while
other host users cannot read them through file permissions. Keep the secrets
directory private (`chmod 700 deploy/compose/secrets`) as an additional layer.
If you change the API's runtime GID, use that GID instead of `1000`. Actual
account passwords are entered in Tandem and are not deployment secrets. Do not
change the secret paths in `.env` unless you move these files.

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

Attach the existing Nginx container to `tandem-edge`, or set
`TANDEM_EDGE_NETWORK` to its existing external Docker network. Add the two
files under `deploy/nginx/` to the matching Nginx contexts. The API location
must precede the Actual catch-all. Set the required `TRUST_PROXY` value to the
exact proxy address or narrow CIDR observed by the API; production Compose
refuses an empty value. Do not trust all forwarded headers. Keep
TLS termination and the Actual server's existing proxy settings in the
existing Nginx site configuration.
The example limits general API traffic to 600 requests per minute with a burst
of 60, and applies an edge limit of 10 requests per minute with a burst of 5 to
password login, OpenID start, OpenID completion, and the Actual callback.
Nginx keys these zones on `$binary_remote_addr`, the immediate peer address it
sees. If Nginx is behind a CDN or another proxy, configure Nginx real-IP
handling for only that upstream or the edge limit will group clients under the
upstream's address.

Each API process shares one in-memory 10-requests-per-60-seconds limit across
Actual-password login and OpenID start. It keys by client address and groups
IPv6 addresses by `/64`. Fastify uses the direct peer address unless
`TRUST_PROXY` lists the proxy addresses it should trust when reading the
forwarded chain. For a proxy chain, configure Nginx real-IP handling only for
the upstream proxy and list the trusted hops in `TRUST_PROXY`; otherwise the
API may also group clients under an upstream address. With no trusted proxy,
clients behind one proxy share the API process's limit. Keep the API origin
private so clients cannot bypass Nginx's edge limits. The API limit is local to
each process; use a shared limiter before running multiple API processes or
replicas. The API location compresses JSON responses of at least 1 KiB when
the client supports gzip.

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
and unexpected request exceptions are logged by error type only; messages,
stack traces, and connection strings are omitted. Neither the database nor API
ports are published on the host. Allow resource monitoring and persistent
volume monitoring at the Docker host level.

In Tandem, enter the public API address and sign in with the Actual account.
Available budgets come from that account's Actual budget catalog.

For remote sign-in, use HTTPS. HTTP does not encrypt the Actual password or
session credentials in transit; use it only on a trusted local network. The
Actual OpenID callback URL carries a short-lived Actual session token in its
query string, so this example disables access logging and suppresses
non-emergency error logs for that callback path. Custom reverse proxies must
also omit its query string from access and error logs.

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
