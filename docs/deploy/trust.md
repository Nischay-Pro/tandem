# Trust and security for operators

Tandem's API can read budget data through Actual's supported Node client. Treat
the API host, its PostgreSQL database, the Actual workspace volume, and backups
as sensitive. The server also stores encrypted Actual session tokens so the
app does not send the Actual password on every request.

Before deployment:

- Read [Architecture and trust](../concepts/architecture-and-trust.md) to see
  how unencrypted and encrypted budgets pass through the service.
- Keep PostgreSQL and the API off public host ports; route app traffic through
  HTTPS at a trusted reverse proxy.
- Set `TANDEM_TRUST_PROXY` to the proxy address or a narrow CIDR. Keep callback query
  strings out of access and error logs.
- Protect the Actual workspace volume and backup files. Backups can contain
  readable budget data; file permissions alone do not encrypt them.
- Keep the session encryption key with protected backups, separately from the
  backup files. See [key configuration](configuration.md) and
  [backup and restore](backup-restore.md).

## Verify a published API image

Nightly API images are built from a main commit after that exact commit passes
CI. The workflow tests the candidate with the production Compose stack before
publishing it, then attaches GitHub build provenance. Verify a digest-pinned
image before deploying it:

```sh
cosign verify \
  --certificate-identity 'https://github.com/Nischay-Pro/tandem/.github/workflows/nightly-api.yml@refs/heads/main' \
  --certificate-oidc-issuer https://token.actions.githubusercontent.com \
  ghcr.io/nischay-pro/tandem-api@sha256:IMAGE_DIGEST

gh attestation verify \
  oci://ghcr.io/nischay-pro/tandem-api@sha256:IMAGE_DIGEST \
  --repo Nischay-Pro/tandem
```

Replace `IMAGE_DIGEST` with the 64 hexadecimal characters from the API image
reference in `deploy/compose/production.yml`. The
[setup command](install.md#start-a-new-deployment) installs the Compose bundle
from the current nightly release; it does not silently update a running
installation.

For user-side offline behavior and status messages, see
[Offline sync and conflicts](../using/offline-sync-and-conflicts.md).
