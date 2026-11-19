# Trust and security for operators

Tandem's API can read budget data through Actual's supported Node client. Treat
the API host, its PostgreSQL database, the Actual workspace volume, and backups
as sensitive. The server stores encrypted Actual session tokens in PostgreSQL.

- Keep PostgreSQL private and expose the API only through a trusted HTTPS reverse proxy.
- Keep the install directory's `.env` private. It contains database credentials and the session encryption key.
- Set `TRUST_PROXY` to the proxy address or a narrow CIDR. Keep callback query strings out of access and error logs.
- Protect the Actual workspace volume and backup files. Backups can contain readable budget data.
- Keep `AUTH_TOKEN_ENCRYPTION_KEY` stable and include it in protected backup storage.

Nightly API images are published as `ghcr.io/nischay-pro/tandem-api:latest-nightly`.
The setup command defaults to this tag. To pin a particular version, set
`TANDEM_API_TAG` in `.env` to the desired image tag.

For the data flow, see [Architecture and trust](../concepts/architecture-and-trust.md).
For user-side offline behavior and status messages, see
[Offline sync and conflicts](../using/offline-sync-and-conflicts.md).
