# Back up and restore

Your Actual server holds the budgets; back it up as Actual recommends. Extension
sync holds only sealed planning records, but back it up too so devices can
recover them.

## Back up

```sh
deploy/tandem-data.py backup /secure/tandem-backups/tandem-$(date +%Y%m%d)
```

Set `TANDEM_BACKUP_GPG_PASSPHRASE_FILE` to a passphrase file to encrypt the dump
with GPG. Keep the passphrase apart from the backup.

For a nightly backup, run `deploy/tandem-data.py scheduled-backup`. It writes to
`TANDEM_BACKUP_DIRECTORY` and records the last success. `deploy/tandem-data.py
status` fails when the last backup is older than `TANDEM_BACKUP_MAX_AGE_HOURS`
(26 by default), so a monitor can alert on it.

A failed backup removes its partial file and leaves the last-success record
unchanged.

## Restore

```sh
deploy/tandem-data.py restore /secure/tandem-backups/tandem-20261121
```

Restore checks the dump first, takes a safety backup of the current data, then
restores in a single transaction and restarts the service. If anything fails,
the database is left as it was and the command prints how to return to the
safety backup.
