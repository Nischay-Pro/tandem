# Back up and restore Tandem

For operators protecting or recovering a Tandem deployment.

## What you need

- A protected backup destination separate from the Tandem host and Compose
  volumes.
- Access to the installation directory and the host's `docker` command.
- The passphrase file if the backups are encrypted.
- GnuPG installed on the host to encrypt or restore encrypted backup files.

The backup scripts capture PostgreSQL and the Actual API workspace. They do
not include the deployment's `.env` or secret files. Keep live credentials in
a protected secrets manager. Store a protected copy of the session encryption
key separately from the backup files, but make it available in the same
recovery set; it is needed to read saved sessions in the database backup.
Backups may contain notes, transaction data, and readable Actual budget files.
The default backup files use mode 600 but are plaintext; permissions do not
encrypt them.

## Create a manual backup

1. **Choose a new prefix in protected storage** and run the backup script.
   The default Compose project is `tandem`. If you included Tandem in the
   existing Actual Compose project, set `TANDEM_COMPOSE_PROJECT` to that
   project's name and keep `TANDEM_ENV_FILE` pointed at Tandem's `.env`:

   ```sh
   TANDEM_COMPOSE_PROJECT=tandem \
     TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env \
     /opt/tandem/deploy/backup.sh /secure/tandem-backups/tandem-2026-11-12
   ```

   The API is briefly stopped while its Actual workspace is archived, then
   restarted. Do not reuse a prefix with existing backup files.

   **Check:** the command reports success and the prefix has both a PostgreSQL
   dump and an Actual workspace archive. The directory also contains an updated
   `tandem-backup.last-success` marker. If the command fails, keep the previous
   verified backup, check the API and PostgreSQL logs, and retry with a new
   prefix after the service is healthy.

To encrypt the output with GnuPG, first create a passphrase file outside the
backup directory and store another copy in a password manager:

```sh
install -d -m 700 /secure/tandem-backup-keys
openssl rand -hex 32 > /secure/tandem-backup-keys/passphrase
chmod 600 /secure/tandem-backup-keys/passphrase
```

Then pass its path to the backup script. It encrypts each backup stream before
writing it to disk and adds `.gpg` to each output file:

```sh
TANDEM_COMPOSE_PROJECT=tandem \
  TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env \
  TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase \
  /opt/tandem/deploy/backup.sh /secure/tandem-backups/tandem-2026-11-12
```

**Check:** both encrypted files exist and are non-empty. Verify each can be
decrypted without writing plaintext to disk:

```sh
for file in /secure/tandem-backups/tandem-2026-11-12.*.gpg; do
  gpg --batch --yes --pinentry-mode loopback \
    --passphrase-file /secure/tandem-backup-keys/passphrase \
    --decrypt --output /dev/null "$file"
done
```

If decryption fails, preserve the passphrase and create a new backup with a new
prefix. Never store the passphrase beside the backups.

## Schedule backups

The supplied systemd timer runs once per day at 03:00, with up to a 15-minute
random delay. It briefly stops the API. Choose a quiet time and store the
backup directory on protected storage separate from the host.

1. **Create the operator environment file.** For an installation at
   `/opt/tandem`, set the Compose project, environment-file path, and backup
   destination. Add the GnuPG setting only if each output file should be
   encrypted:

   ```sh
   sudo install -d -m 700 /etc/tandem /secure/tandem-backups
   sudo tee /etc/tandem/backup.env >/dev/null <<'EOF'
   TANDEM_COMPOSE_PROJECT=tandem
   TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env
   TANDEM_BACKUP_DIRECTORY=/secure/tandem-backups
   TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase
   EOF
   sudo chmod 600 /etc/tandem/backup.env
   ```

   **Check:** the environment file is readable only by root and its paths
   match the installation and protected backup storage. If using plaintext
   backups on encrypted storage, remove the GnuPG line. For a Compose include,
   set `TANDEM_COMPOSE_PROJECT` to the existing Actual project's name instead
   of `tandem`.

2. **Install and enable the supplied timer:**

   ```sh
   sudo install -m 644 /opt/tandem/deploy/systemd/tandem-backup.service /etc/systemd/system/
   sudo install -m 644 /opt/tandem/deploy/systemd/tandem-backup.timer /etc/systemd/system/
   sudo systemctl daemon-reload
   sudo systemctl enable --now tandem-backup.timer
   ```

   **Check:** `systemctl list-timers tandem-backup.timer` shows the next run.
   If it does not, check the unit files and systemd journal before relying on
   the schedule.

3. **Run one backup and verify it:**

   ```sh
   sudo systemctl start tandem-backup.service
   sudo /opt/tandem/deploy/backup-status.sh /secure/tandem-backups
   sudo systemctl status tandem-backup.service --no-pager
   ```

   **Check:** the status command reports a complete, non-empty backup pair
   younger than 26 hours. If it fails, read `journalctl -u tandem-backup.service
--since today`, correct the paths or credentials, and run the service again.

Keep at least 30 daily backup pairs as a starting point, then copy older
verified pairs off-host according to your retention needs. Confirm the
protected copy of the session encryption key and the other required secret
values are available before restoring.

## Restore a backup

Restore during a maintenance window. The restore script validates both inputs,
creates and prints a safety-backup prefix for the current data, then stops the
API. It leaves the API stopped for you to verify the result.

1. **Choose one complete backup pair.** Provide either both plaintext files or
   both `.gpg` files. For an encrypted pair, set
   `TANDEM_BACKUP_GPG_PASSPHRASE_FILE` to the same protected passphrase file
   used to create it.

   **Check:** the prefix points to a `.postgres.dump` file and an
   `.actual-workspace.tar.gz` file, or both files with the additional `.gpg`
   suffix. Do not mix encrypted and plaintext files. If validation fails, keep
   the running deployment and choose a complete verified pair.

2. **Run the restore script:**

   ```sh
   TANDEM_COMPOSE_PROJECT=tandem \
     TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env \
     /opt/tandem/deploy/restore.sh /secure/tandem-backups/tandem-2026-11-12
   ```

   For an encrypted pair, add
   `TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase`
   to the command's environment. For a Compose include, use the existing
   Actual project's name for `TANDEM_COMPOSE_PROJECT`.

   **Check:** the script reports that both the database and workspace were
   restored, and prints commands for starting the API. Record the safety
   prefix it prints. If restore is interrupted, leave the API stopped and use
   that prefix to recover the pre-restore database and workspace:

   ```sh
   TANDEM_COMPOSE_PROJECT=tandem \
     TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env \
     /opt/tandem/deploy/restore.sh \
       /secure/tandem-backups/tandem-2026-11-12.before-restore-20261112T061249Z-12345
   ```

   Use the exact prefix from your restore output; the example timestamp is not
   literal. For an encrypted safety copy, also set its GnuPG passphrase file.

3. **Start Tandem using the same Compose mode as the installation.** For a
   standalone deployment at `/opt/tandem`:

   ```sh
   docker compose --project-directory /opt/tandem \
     --env-file /opt/tandem/deploy/compose/.env \
     -f /opt/tandem/deploy/compose/production.yml up -d tandem-api
   ```

   For a Compose include, run `up -d tandem-api` from the existing Actual
   project instead. Then check readiness:

   ```sh
   curl --fail https://budget.example.com/api/v1/health/ready
   ```

   **Check:** readiness returns `{"status":"ok"}` and a signed-in phone can
   load the restored budget. The API starts with an empty snapshot cache, so
   phones download a fresh snapshot. If readiness or the budget read fails,
   leave the service in maintenance and follow
   [Troubleshooting](troubleshooting.md).

Test restores regularly using a separate, disposable Compose project. Never
test a restore against the only copy of a budget.

For deployment upgrades and rollbacks, see the
[upgrade guide](upgrade.md). For recovery errors, see
[troubleshooting](troubleshooting.md).
