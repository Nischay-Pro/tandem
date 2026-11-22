# Configuration reference

Extension sync reads these settings from `.env`.

| Setting                | Required | Meaning                                                                                                          |
| ---------------------- | -------- | ---------------------------------------------------------------------------------------------------------------- |
| `ACTUAL_SERVER_URL`    | Yes      | The Actual server address reachable from the container. The service asks it whether a session can open a budget. |
| `POSTGRES_PASSWORD`    | Yes      | Password for the bundled PostgreSQL database. The setup script generates it.                                     |
| `TANDEM_API_TAG`       | No       | Image tag to run. Defaults to `latest-nightly`.                                                                  |
| `TANDEM_API_HOST_PORT` | No       | Loopback port for a host-level proxy. Defaults to `19753`.                                                       |
| `TANDEM_EDGE_NETWORK`  | No       | Docker network shared with the reverse proxy. Defaults to `tandem-edge`.                                         |
| `TRUST_PROXY`          | No       | Kept for the Nginx setup script. The service doesn't use it.                                                     |

The service stores only sealed planning records and wrapped keys. It holds no
Actual credentials.
