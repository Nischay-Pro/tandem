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
image before deploying it. You need `cosign`, GitHub CLI, and the digest from
the installed `production.yml`.

1. **Read the digest-pinned image reference** from
   `deploy/compose/production.yml`. It must use the Tandem API image and include
   `@sha256:` followed by 64 hexadecimal characters.

   **Check:** the reference is digest-pinned, not a mutable tag such as
   `:nightly`. If it does not match, do not deploy that image.

2. **Verify the image signature and GitHub build attestation:**

   ```sh
   cosign verify \
     --certificate-identity 'https://github.com/Nischay-Pro/tandem/.github/workflows/nightly-api.yml@refs/heads/main' \
     --certificate-oidc-issuer https://token.actions.githubusercontent.com \
     ghcr.io/nischay-pro/tandem-api@sha256:IMAGE_DIGEST

   gh attestation verify \
     oci://ghcr.io/nischay-pro/tandem-api@sha256:IMAGE_DIGEST \
     --repo Nischay-Pro/tandem
   ```

   Replace `IMAGE_DIGEST` with the 64 hexadecimal characters from the API
   image reference in `deploy/compose/production.yml`.

   **Check:** both commands exit successfully. If either fails, confirm the
   digest and repository name; do not deploy until signature and provenance
   verification pass. The [setup command](install.md#install-and-start)
   installs the Compose bundle from the current nightly release; it does not
   silently update a running installation.

For user-side offline behavior and status messages, see
[Offline sync and conflicts](../using/offline-sync-and-conflicts.md).
