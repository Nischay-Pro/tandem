# Server configuration

This reference is for the operator running Tandem's API with the supplied
production Compose file. Setup writes operator settings to
`tandem/deploy/compose/.env` and installs the digest-pinned Compose file from
the nightly release. Secret values are mounted from files and are never stored
in `.env`.

## API settings

| Setting                                                                           | Required or default                                    | Purpose                                                                                                                                            |
| --------------------------------------------------------------------------------- | ------------------------------------------------------ | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| `DATABASE_URL` or `DATABASE_URL_FILE`                                             | Required                                               | PostgreSQL URL. Use one form, not both. The file path must be absolute inside the container.                                                       |
| `AUTH_TOKEN_ENCRYPTION_KEY` or `AUTH_TOKEN_ENCRYPTION_KEY_FILE`                   | Required                                               | Random 32-byte key encoded as 64 hexadecimal characters. Keep it stable and back it up with the database.                                          |
| `AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS` or `AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE` | Optional; empty means no previous key                  | Temporary key used during key rotation. Remove the previous key only after old backups no longer need it.                                          |
| `HOST`                                                                            | `0.0.0.0`                                              | Address the API listens on inside its container.                                                                                                   |
| `PORT`                                                                            | `19753`                                                | API port. Must be an integer from 1 to 65535.                                                                                                      |
| `TRUST_PROXY`                                                                     | Empty                                                  | Comma-separated proxy IP addresses or narrow CIDRs as seen by the API. Empty means forwarded headers are not trusted. Never use a trust-all value. |
| `ACTUAL_SERVER_URL`                                                               | Optional                                               | Actual address reachable from the API container. Required when an operator-managed Actual session token is configured.                             |
| `ACTUAL_SESSION_TOKEN` or `ACTUAL_SESSION_TOKEN_FILE`                             | Optional; the supplied secret file is empty by default | Optional operator-managed Actual session. Most installations leave this unset and let each person sign in through the app.                         |
| `ACTUAL_PUBLIC_URL`                                                               | Optional                                               | Public Tandem URL used for Actual OpenID sign-in. Use the URL served through the reverse proxy.                                                    |
| `ACTUAL_API_DATA_DIR`                                                             | `/data/actual-api`                                     | Absolute path for the Actual API's local workspace.                                                                                                |

For each setting with a secret-file form, provide either the direct value or
the `_FILE` path, never both. File paths are resolved inside the API container.
The API removes one trailing newline from a secret file. `ACTUAL_SERVER_PASSWORD`
is not supported; people sign in through Tandem, or an operator configures an
Actual session token. The supplied Compose file maps the Tandem-prefixed values
below to these API settings.

## Compose settings

| Setting                                          | Required or default                                         | Purpose                                                                                                                                                                    |
| ------------------------------------------------ | ----------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `TANDEM_POSTGRES_DB`                             | `tandem`                                                    | Tandem database name; prefixed to avoid collisions with an existing Compose project's variables.                                                                           |
| `TANDEM_POSTGRES_USER`                           | `tandem`                                                    | Tandem database role; prefixed to avoid collisions with an existing Compose project's variables.                                                                           |
| `TANDEM_API_IMAGE`                               | Not used by the downloaded nightly bundle                   | The checked-in Compose template uses this required value for local or manually selected images. The downloaded nightly bundle pins the image directly in `production.yml`. |
| `TANDEM_POSTGRES_PASSWORD_FILE`                  | `deploy/compose/secrets/postgres_password`                  | Host path to the Postgres password file.                                                                                                                                   |
| `TANDEM_DATABASE_URL_FILE`                       | `deploy/compose/secrets/database_url`                       | Host path to Tandem's PostgreSQL connection URL file.                                                                                                                      |
| `TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_FILE`          | `deploy/compose/secrets/auth_token_encryption_key`          | Host path to the key that encrypts saved Actual sessions.                                                                                                                  |
| `TANDEM_AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS_FILE` | `deploy/compose/secrets/auth_token_encryption_key_previous` | Host path to the previous key; the supplied empty file is used outside key rotation.                                                                                       |
| `TANDEM_ACTUAL_SESSION_TOKEN_FILE`               | `deploy/compose/secrets/actual_session_token` (empty)       | Host path to an optional operator-managed Actual session token.                                                                                                            |
| `TANDEM_EDGE_NETWORK`                            | `tandem-edge`                                               | External Docker network shared by Tandem, Actual and the reverse proxy.                                                                                                    |
| `TANDEM_TRUST_PROXY`                             | Empty                                                       | Proxy IP or narrow CIDR as seen by the API; empty disables forwarded-address trust.                                                                                        |
| `TANDEM_ACTUAL_SERVER_URL`                       | Empty                                                       | Actual service address reachable on the shared Docker network.                                                                                                             |
| `TANDEM_ACTUAL_PUBLIC_URL`                       | Empty                                                       | Public Tandem HTTPS URL used for Actual OpenID sign-in.                                                                                                                    |

The setup command creates the secret files and preserves an existing `.env`.
The downloaded `production.yml` and `.env.example` are attached to the same
nightly build and keep the tested API image and Compose definition together.
The API and Postgres service names, Compose secret keys, networks, and volumes
use Tandem-specific names so they can be included beside an existing Actual
Compose project without taking over its common service names or environment
variables.

The supplied Compose file mounts the PostgreSQL password, database URL, API
encryption keys and optional Actual session token from
`tandem/deploy/compose/secrets/`. Create them using the
[installation steps](install.md#install-and-start).

## Optional operator-managed Actual session

Leave `TANDEM_ACTUAL_SESSION_TOKEN_FILE` pointing to the supplied empty file
for normal per-person sign-in. An operator-managed token is only for a setup
where the API must open an Actual session before anyone signs in. Anyone with
this token can access the budgets of that Actual account. Protect it like a
password and keep it in the secret file, never `.env`, shell history, or a
command-line argument. When it is replaced or revoked, recreate the API and
check readiness.

## Rotate the session encryption key

The 32-byte `AUTH_TOKEN_ENCRYPTION_KEY` encrypts Actual session tokens stored in
PostgreSQL. Keep it stable during normal operation. Before rotating, make a
verified database backup and copy the current key into protected storage. The
key is required to read tokens in that backup. For an installation at
`/opt/tandem`, for example:

```sh
sudo install -d -m 700 /secure/tandem-key-backups
sudo install -o root -g root -m 600 \
  /opt/tandem/deploy/compose/secrets/auth_token_encryption_key \
  /secure/tandem-key-backups/auth_token_encryption_key
```

Replace `/opt/tandem` with the installation directory. Keep this copy separate
from the live secret directory and protect it with the database backup.

1. Create a new random key in protected storage:

   ```sh
   openssl rand -hex 32 > /secure/new-auth-key
   chmod 600 /secure/new-auth-key
   ```

   **Check:** the file contains 64 hexadecimal characters. If it does not,
   generate it again; do not replace the current key.

2. Install the new key as current and the former key as previous, preserving
   the API's owner and mode:

   ```sh
   sudo install -o root -g 1000 -m 0440 /secure/new-auth-key \
     /opt/tandem/deploy/compose/secrets/auth_token_encryption_key
   sudo install -o root -g 1000 -m 0440 \
     /secure/tandem-key-backups/auth_token_encryption_key \
     /opt/tandem/deploy/compose/secrets/auth_token_encryption_key_previous
   ```

   Replace `/opt/tandem` if Tandem is installed elsewhere. Recreate the API
   service so it reads the new secret files:

   ```sh
   docker compose --project-directory /opt/tandem \
     --env-file /opt/tandem/deploy/compose/.env \
     -f /opt/tandem/deploy/compose/production.yml \
     up -d --force-recreate tandem-api
   ```

   For a Compose include, run `up -d --force-recreate tandem-api` through the
   existing Actual project instead.

   **Check:** the API reports healthy and existing users can still access
   their budgets. If startup fails, restore the old key as current, empty the
   previous-key file, recreate the API, and stop before attempting another
   rotation.

3. After startup confirms that stored tokens were re-encrypted, replace the
   previous-key file with an empty file:

   ```sh
   sudo install -o root -g 1000 -m 0440 /dev/null \
     /opt/tandem/deploy/compose/secrets/auth_token_encryption_key_previous
   ```

   Recreate the API service again using the same Compose command from step 2.
   **Check:** readiness succeeds and the app still signs in. Keep the old key in
   protected backup storage while any database backup may still contain tokens
   encrypted with it.

## Check the configuration

After editing `.env` or secret files, start or recreate the API and check its
readiness endpoint:

```sh
docker compose --project-directory "$PWD/tandem" \
  --env-file "$PWD/tandem/deploy/compose/.env" \
  -f "$PWD/tandem/deploy/compose/production.yml" up -d
curl --fail https://budget.example.com/api/v1/health/ready
```

If the check fails, use the [troubleshooting guide](troubleshooting.md). For
network and proxy setup, see [Reverse proxy setup](reverse-proxy.md).
