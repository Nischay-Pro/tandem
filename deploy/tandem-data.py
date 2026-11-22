#!/usr/bin/env python3
"""Back up, restore, schedule, and check Tandem data using one command."""

from __future__ import annotations

import argparse
import os
from pathlib import Path
import shutil
import shlex
import subprocess
import sys
import tempfile
from datetime import datetime, timezone

PROJECT_ROOT = Path(__file__).resolve().parent.parent
COMPOSE_FILE = PROJECT_ROOT / "deploy" / "docker-compose.yml"


def command(*args: str, check: bool = True, **kwargs):
    return subprocess.run(args, check=check, **kwargs)


def compose() -> list[str]:
    env_file = Path(os.environ.get("TANDEM_ENV_FILE", PROJECT_ROOT / ".env"))
    if not env_file.is_file():
        raise RuntimeError(f"Missing {env_file}; set TANDEM_ENV_FILE to the install .env.")
    return [
        "docker", "compose", "-p", os.environ.get("TANDEM_COMPOSE_PROJECT", "tandem"),
        "--project-directory", str(PROJECT_ROOT), "--env-file", str(env_file),
        "-f", str(COMPOSE_FILE),
    ]


def passphrase_file() -> Path | None:
    value = os.environ.get("TANDEM_BACKUP_GPG_PASSPHRASE_FILE", "")
    if not value:
        return None
    path = Path(value)
    if not path.is_file() or not os.access(path, os.R_OK) or path.stat().st_size == 0:
        raise RuntimeError("TANDEM_BACKUP_GPG_PASSPHRASE_FILE must name a non-empty, readable passphrase file.")
    if shutil.which("gpg") is None:
        raise RuntimeError("Install GnuPG on this host to encrypt or restore encrypted backup files.")
    return path


def run_pipe_to_file(producer: list[str], output: Path, key_file: Path | None) -> None:
    if key_file is None:
        with output.open("wb") as stream:
            process = subprocess.run(producer, stdout=stream, check=False)
            if process.returncode:
                raise subprocess.CalledProcessError(process.returncode, producer)
        return
    source = subprocess.Popen(producer, stdout=subprocess.PIPE)
    assert source.stdout is not None
    encrypt = subprocess.run(
        ["gpg", "--batch", "--yes", "--pinentry-mode", "loopback",
         "--passphrase-file", str(key_file), "--symmetric", "--cipher-algo",
         "AES256", "--output", str(output)],
        stdin=source.stdout, stdout=subprocess.DEVNULL, check=False,
    )
    source.stdout.close()
    source_code = source.wait()
    if source_code:
        raise subprocess.CalledProcessError(source_code, producer)
    if encrypt.returncode:
        raise subprocess.CalledProcessError(encrypt.returncode, "gpg")


def decrypt_backup(prefix: Path, temporary_directory: Path) -> tuple[Path, bool, Path | None]:
    plain_db = Path(f"{prefix}.postgres.dump")
    encrypted_db = Path(f"{plain_db}.gpg")
    if plain_db.is_file() and not encrypted_db.exists():
        return plain_db, False, None
    if not (encrypted_db.is_file() and not plain_db.exists()):
        raise RuntimeError("Provide exactly one plaintext or one .gpg backup for this prefix.")
    key_file = passphrase_file()
    if key_file is None:
        raise RuntimeError("Set TANDEM_BACKUP_GPG_PASSPHRASE_FILE to the passphrase file used for this backup.")
    db_temp = temporary_directory / "restore.postgres.dump"
    command("gpg", "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-file", str(key_file),
            "--decrypt", "--output", str(db_temp), str(encrypted_db))
    db_temp.chmod(0o600)
    return db_temp, True, key_file


def backup(prefix: Path) -> None:
    prefix.parent.mkdir(parents=True, exist_ok=True)
    prefix = prefix.parent.resolve() / prefix.name
    key_file = passphrase_file()
    encrypted = key_file is not None
    db_output = Path(f"{prefix}{'.postgres.dump.gpg' if encrypted else '.postgres.dump'}")
    if any(Path(f"{prefix}{suffix}").exists() for suffix in (".postgres.dump", ".postgres.dump.gpg")):
        raise RuntimeError("Backup files already exist for this prefix; choose a new prefix or move them first.")

    compose_command = compose()
    db_fd, db_temp_name = tempfile.mkstemp(prefix=f".{prefix.name}.db-", dir=prefix.parent)
    os.close(db_fd)
    db_temp = Path(db_temp_name)
    db_temp.chmod(0o600)
    marker = prefix.parent / "tandem-backup.last-success"
    success = False
    try:
        run_pipe_to_file(
            [*compose_command, "exec", "-T", "tandem-postgres", "sh", "-ec",
             'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'],
            db_temp, key_file,
        )
        if db_temp.stat().st_size == 0:
            raise RuntimeError("The backup file is empty; the backup was not saved.")
        os.replace(db_temp, db_output)
        marker_tmp = marker.with_name(f".{marker.name}.{os.getpid()}.tmp")
        marker_tmp.write_text(
            f"{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}\n{prefix.name}\n",
            encoding="utf-8",
        )
        marker_tmp.chmod(0o600)
        os.replace(marker_tmp, marker)
        success = True
        if encrypted:
            print("Wrote an encrypted PostgreSQL backup with mode 600. Keep the passphrase separate from the backup.")
        else:
            print("Wrote a plaintext PostgreSQL backup with mode 600. Planning records in it are already end-to-end encrypted.")
        print(f"Last successful backup recorded in {marker}")
    finally:
        db_temp.unlink(missing_ok=True)
        if not success:
            db_output.unlink(missing_ok=True)


def restore(prefix: Path) -> None:
    prefix = prefix.parent.resolve() / prefix.name
    compose_command = compose()
    with tempfile.TemporaryDirectory(prefix="tandem-restore-") as temporary:
        db_input, _encrypted, key_file = decrypt_backup(prefix, Path(temporary))
        db_input.chmod(0o600)
        with db_input.open("rb") as source:
            command(*compose_command, "exec", "-T", "tandem-postgres", "pg_restore", "--list", stdin=source, stdout=subprocess.DEVNULL)

        safety_prefix = Path(f"{prefix}.before-restore-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{os.getpid()}")
        backup(safety_prefix)
        print(f"Safety backup prefix: {safety_prefix}")

        command(*compose_command, "stop", "tandem-api")
        try:
            restore_postgres = (
                'pg_restore --exit-on-error --single-transaction --clean --if-exists --no-owner '
                '--username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'
            )
            with db_input.open("rb") as source:
                command(*compose_command, "exec", "-T", "tandem-postgres", "sh", "-ec", restore_postgres, stdin=source)
        except Exception:
            print("Restore did not complete; the database transaction was rolled back.", file=sys.stderr)
            env = [
                f"TANDEM_COMPOSE_PROJECT={os.environ.get('TANDEM_COMPOSE_PROJECT', 'tandem')}",
                f"TANDEM_ENV_FILE={os.environ.get('TANDEM_ENV_FILE', PROJECT_ROOT / '.env')}",
            ]
            if key_file:
                env.append(f"TANDEM_BACKUP_GPG_PASSPHRASE_FILE={key_file}")
            recovery = [*env, str(Path(__file__).resolve()), "restore", str(safety_prefix)]
            print(f"To return to the pre-restore data, run: {shlex.join(recovery)}", file=sys.stderr)
            raise
        finally:
            command(*compose_command, "start", "tandem-api", stdout=subprocess.DEVNULL)
    print("Database restored and the extension service restarted.")


def status(backup_directory: Path, max_age_hours: int) -> None:
    if max_age_hours < 1:
        raise RuntimeError("TANDEM_BACKUP_MAX_AGE_HOURS must be a positive whole number.")
    marker = backup_directory / "tandem-backup.last-success"
    if not marker.is_file() or marker.stat().st_size == 0:
        raise RuntimeError(f"No successful Tandem backup is recorded in {backup_directory}.")
    values = marker.read_text(encoding="utf-8").splitlines()
    if len(values) != 2 or not values[0] or not values[1] or "/" in values[1]:
        raise RuntimeError("The Tandem backup success marker is invalid.")
    try:
        completed = datetime.strptime(values[0], "%Y-%m-%dT%H:%M:%SZ").replace(tzinfo=timezone.utc)
    except ValueError as error:
        raise RuntimeError("The Tandem backup success marker has an invalid timestamp.") from error
    age = (datetime.now(timezone.utc) - completed).total_seconds()
    if age < 0 or age > max_age_hours * 3600:
        raise RuntimeError(f"The last Tandem backup is older than {max_age_hours} hours ({values[0]}).")
    prefix = backup_directory / values[1]
    db_plain = Path(f"{prefix}.postgres.dump")
    db_encrypted = Path(f"{db_plain}.gpg")
    if db_plain.is_file() and db_plain.stat().st_size and not db_encrypted.exists():
        kind = "plaintext"
    elif db_encrypted.is_file() and db_encrypted.stat().st_size and not db_plain.exists():
        kind = "encrypted"
    else:
        raise RuntimeError("The last-success marker does not point to a complete backup.")
    print(f"OK: last {kind} Tandem backup completed at {values[0]} ({values[1]}).")


def scheduled_backup() -> None:
    directory = Path(os.environ.get("TANDEM_BACKUP_DIRECTORY", "/secure/tandem-backups"))
    directory.mkdir(parents=True, exist_ok=True, mode=0o700)
    directory.chmod(0o700)
    prefix = directory / f"tandem-{datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%SZ')}-{os.getpid()}"
    backup(prefix)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    subcommands = parser.add_subparsers(dest="action", required=True)
    backup_parser = subcommands.add_parser("backup", help="write a PostgreSQL backup")
    backup_parser.add_argument("prefix", type=Path)
    restore_parser = subcommands.add_parser("restore", help="restore a backup")
    restore_parser.add_argument("prefix", type=Path)
    status_parser = subcommands.add_parser("status", help="check the most recent backup")
    status_parser.add_argument("directory", nargs="?", type=Path,
                               default=Path(os.environ.get("TANDEM_BACKUP_DIRECTORY", "/secure/tandem-backups")))
    scheduled_parser = subcommands.add_parser("scheduled-backup", help="write a timestamped scheduled backup")
    scheduled_parser.set_defaults(action="scheduled-backup")
    args = parser.parse_args()
    try:
        if args.action == "backup":
            backup(args.prefix)
        elif args.action == "restore":
            restore(args.prefix)
        elif args.action == "status":
            max_age = int(os.environ.get("TANDEM_BACKUP_MAX_AGE_HOURS", "26"))
            status(args.directory, max_age)
        else:
            scheduled_backup()
    except (RuntimeError, OSError, ValueError, subprocess.CalledProcessError) as error:
        print(f"Tandem data command failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
