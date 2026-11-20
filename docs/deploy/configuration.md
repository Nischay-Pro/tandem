# Configuration

Tandem settings live in the single `.env` file beside `docker-compose.yml`.
Keep that file private: it contains the PostgreSQL password and the key used to
encrypt saved Actual sessions. The setup command reuses `.env` on later runs.
Automatic Nginx setup can update `TANDEM_API_HOST_PORT` and `TRUST_PROXY`.

| Variable                             | Default                  | Purpose                                                                                                      |
| ------------------------------------ | ------------------------ | ------------------------------------------------------------------------------------------------------------ |
| `TANDEM_API_TAG`                     | `latest-nightly`         | Tandem API Docker image tag.                                                                                 |
| `TANDEM_API_HOST_PORT`               | `19753`                  | Loopback port used by host-installed Nginx.                                                                  |
| `TANDEM_EDGE_NETWORK`                | `tandem-edge`            | External Docker network shared with a Docker reverse proxy.                                                  |
| `POSTGRES_PASSWORD`                  | Generated on first setup | PostgreSQL password.                                                                                         |
| `AUTH_TOKEN_ENCRYPTION_KEY`          | Generated on first setup | Stable 32-byte key, encoded as 64 hex characters. Keep it when updating Tandem and with any database backup. |
| `AUTH_TOKEN_ENCRYPTION_KEY_PREVIOUS` | Empty                    | Previous key during encryption-key rotation. Normally leave empty.                                           |
| `TRUST_PROXY`                        | Empty                    | Exact proxy address or narrow CIDR trusted for forwarded client addresses.                                   |
| `ACTUAL_SERVER_URL`                  | Empty                    | Actual server URL reachable by the Tandem API.                                                               |
| `ACTUAL_SESSION_TOKEN`               | Empty                    | Optional operator-managed Actual session. Usually leave empty for per-user sign-in.                          |
| `ACTUAL_PUBLIC_URL`                  | Empty                    | Public HTTPS Tandem URL used for Actual OpenID sign-in.                                                      |

Use `http://actual:5006` for `ACTUAL_SERVER_URL` when the Actual service is
reachable by that name on the shared Docker network. With host-installed Nginx,
the API is published only on loopback. If port `19753` is already occupied,
setup selects a free loopback port and updates `.env` and the Nginx route.

After editing `.env`, recreate the services:

```sh
docker compose up -d
```

Do not change `AUTH_TOKEN_ENCRYPTION_KEY` during a normal update. Existing
sessions stored in PostgreSQL depend on it.
