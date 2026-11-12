# Back up and restore

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
or the same storage account as the backups. Back up `tandem/deploy/compose/.env`
and `tandem/deploy/compose/secrets/` separately in an approved secrets manager.
The `.env` contains non-secret configuration and secret-file paths; the secret
files contain the database password and auth encryption key.

Run a backup to a protected prefix in a directory accessible only to the
operator; output files are created with mode 600. The API is briefly stopped while its Actual
workspace is archived and is restarted afterward.

```sh
tandem/deploy/backup.sh /secure/tandem-backups/tandem-2026-11-01
```

### Back up on a schedule

The supplied systemd timer runs one backup each day at 03:00, with up to a
15-minute randomized delay. It stops the API briefly while it captures the
PostgreSQL database and Actual workspace, then starts the API again. Choose a
quiet time for the deployment. The backup directory should be on protected
storage separate from the Compose volumes and, for disaster recovery, copied
off the host. A practical starting point is to keep at least 30 daily backup
pairs; retain older verified copies on a separate protected destination when
longer history is needed. Keep the auth encryption key with the protected
database backups; without it, saved Actual sign-in sessions in those backups
cannot be decrypted.

The setup command downloads the backup and systemd files into `tandem/deploy/`.
For a standard installation at `/opt/tandem`, create the root-readable
environment file. Omit the GnuPG line if you intentionally store backups on
encrypted storage instead of encrypting each backup file:

```sh
sudo install -d -m 700 /etc/tandem /secure/tandem-backups
sudo tee /etc/tandem/backup.env >/dev/null <<'EOF'
TANDEM_COMPOSE_PROJECT=tandem
TANDEM_ENV_FILE=/opt/tandem/deploy/compose/.env
TANDEM_BACKUP_DIRECTORY=/secure/tandem-backups
TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase
EOF
sudo chmod 600 /etc/tandem/backup.env
sudo install -m 644 /opt/tandem/deploy/systemd/tandem-backup.service /etc/systemd/system/
sudo install -m 644 /opt/tandem/deploy/systemd/tandem-backup.timer /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now tandem-backup.timer
```

Run the service once to verify the paths and credentials. A successful run
creates a timestamped backup pair and atomically updates
`tandem-backup.last-success`. The status command checks that marker, confirms
both backup files still exist and are non-empty, and fails when the last
successful run is older than 26 hours:

```sh
sudo systemctl start tandem-backup.service
sudo /opt/tandem/deploy/backup-status.sh /secure/tandem-backups
sudo systemctl status tandem-backup.service --no-pager
sudo journalctl -u tandem-backup.service --since today
```

The marker is only updated after both files are written and the API has been
restarted successfully. A failed run leaves the prior marker unchanged, so the
status check will fail once that backup becomes stale. Monitor the timer and
status command with the host's normal alerting. To inspect the next scheduled
run, use `systemctl list-timers tandem-backup.timer`.

To write encrypted `.gpg` files instead, pass the passphrase-file path to the
backup script. The backup stream is encrypted before it is written to disk:

```sh
TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase \
  tandem/deploy/backup.sh /secure/tandem-backups/tandem-2026-11-01-encrypted
```

Restore either a plaintext pair or an encrypted `.gpg` pair during a
maintenance window. Encrypted restore requires the same passphrase file and
decrypts into temporary mode-600 files that the script removes when it exits.
The script validates both files before changing the live data. It then makes
and prints a separate safety-backup prefix for the current database and Actual
workspace. Keep that copy until you have verified the restored budget. When
restoring an encrypted pair, the safety copy uses the same passphrase file.

PostgreSQL restore runs in a single transaction with errors enabled, so a
failed database restore rolls back its changes. Workspace files are extracted
and checked in a staging folder; the old workspace is moved aside and is only
removed after the restored files are in place and counted. A rejected archive
leaves the current database and workspace in place and the API running. A
restore interrupted after the database step can leave the API stopped and the
database and workspace from different backups. To restore the safety copy, use
the exact prefix printed by `restore.sh` (shown here with an example timestamp
and process ID):

```sh
tandem/deploy/restore.sh /secure/tandem-backups/tandem-2026-11-01.before-restore-20261112T061249Z-12345
```

The restore script leaves the API stopped until you explicitly start and
verify it. Its new process starts with an empty snapshot cache, so clients
receive a fresh generation and resync automatically.

For an encrypted backup, provide the same passphrase file used when it was
created:

```sh
TANDEM_BACKUP_GPG_PASSPHRASE_FILE=/secure/tandem-backup-keys/passphrase \
  tandem/deploy/restore.sh /secure/tandem-backups/tandem-2026-11-01-encrypted
```

Then start and verify the API using the commands above. Confirm that an
authorized client performs a fresh snapshot before returning the deployment
to normal use. Test backups by restoring them to a separate disposable Compose
project regularly; never test restore against a user's only copy.
Use a separate private Compose project for the restore test and verify its
readiness endpoint and a budget read before relying on the backup set.
