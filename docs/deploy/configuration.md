# Server configuration

This reference is for the operator running Tandem's API with the supplied
production Compose file. Setup writes the digest-pinned API image and Compose
settings to `tandem/deploy/compose/.env`; secret values are mounted from files
and are never stored in that file.

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
Actual session token.

## Compose settings

| Setting               | Required or default | Purpose                                                                               |
| --------------------- | ------------------- | ------------------------------------------------------------------------------------- |
| `POSTGRES_DB`         | `tandem`            | Database created for Tandem.                                                          |
| `POSTGRES_USER`       | `tandem`            | PostgreSQL role used by Tandem.                                                       |
| `TANDEM_API_IMAGE`    | Required            | GHCR API image reference pinned by digest. Setup resolves the nightly digest.         |
| `TANDEM_EDGE_NETWORK` | `tandem-edge`       | Existing Docker network shared by the API, Actual and reverse proxy.                  |
| `TRUST_PROXY`         | Empty               | Proxy address or narrow CIDR used by the API; empty disables forwarded-address trust. |
| `ACTUAL_SERVER_URL`   | Empty               | Actual service address on the shared Docker network.                                  |
| `ACTUAL_PUBLIC_URL`   | Empty               | Public Tandem URL; set it to enable OpenID sign-in.                                   |

The supplied Compose file mounts the PostgreSQL password, database URL, API
encryption keys and optional Actual session token from
`tandem/deploy/compose/secrets/`. Create them using the
[start steps](install.md#start-a-new-deployment).

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
network and proxy setup, see the [installation guide](install.md).
