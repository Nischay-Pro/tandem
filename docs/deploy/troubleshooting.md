# Troubleshooting the Tandem server

Use these checks when the app cannot reach Tandem or the API is not ready. They
do not delete the phone's cached budget or queued changes.

| What you see                                            | Check                                                                                                    | What to do                                                                                                                                         |
| ------------------------------------------------------- | -------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- |
| The app says Tandem is not responding                   | Open `https://your-domain/api/v1/health/ready`.                                                          | Route `/api/v1/` through Nginx to the Tandem API before Actual's catch-all route.                                                                  |
| The readiness check fails                               | Run `docker compose ps` and `docker compose logs tandem-api tandem-postgres` from the install directory. | Correct the reported configuration or storage problem, then run `docker compose up -d`. Keep the Docker volumes intact while a write is uncertain. |
| Sign-in fails but Actual's website opens                | Check `ACTUAL_SERVER_URL` in `.env`; it must be reachable from the API container.                        | Use the Actual service address on a shared Docker network, such as `http://actual:5006`.                                                           |
| OpenID sign-in does not return to Tandem                | Check `ACTUAL_PUBLIC_URL` and confirm Nginx forwards `/api/v1/` to the API.                              | Set `ACTUAL_PUBLIC_URL` to the public HTTPS Tandem address and restart the API.                                                                    |
| Sign-in rate limiting treats many people as one address | Check the proxy address seen by the API.                                                                 | Set `TRUST_PROXY` to that exact address or a narrow CIDR. Do not trust all forwarded headers.                                                      |
| Readiness reports `server_storage_full`                 | Check free space for the PostgreSQL and Actual workspace volumes.                                        | Free or expand storage. Do not delete the workspace volume while writes may be uncertain.                                                          |

When the service is healthy again, the phone can retry pending changes from
Sync. Do not clear app data to troubleshoot a server connection; that can
remove local-only changes.
