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
NODE_IMAGE = "node:26.13.1-trixie-slim@sha256:e6bedef217677b276c591795e169840edd9096dbf13f29df8ef8d1856b8820bc"


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


def decrypt_backup(prefix: Path, temporary_directory: Path) -> tuple[Path, Path, bool, Path | None]:
    plain_db = Path(f"{prefix}.postgres.dump")
    plain_workspace = Path(f"{prefix}.actual-workspace.tar.gz")
    encrypted_db = Path(f"{plain_db}.gpg")
    encrypted_workspace = Path(f"{plain_workspace}.gpg")
    if plain_db.is_file() and plain_workspace.is_file() and not encrypted_db.exists() and not encrypted_workspace.exists():
        return plain_db, plain_workspace, False, None
    if not (encrypted_db.is_file() and encrypted_workspace.is_file() and not plain_db.exists() and not plain_workspace.exists()):
        raise RuntimeError("Provide exactly one complete plaintext backup pair or one complete .gpg backup pair for this prefix.")
    key_file = passphrase_file()
    if key_file is None:
        raise RuntimeError("Set TANDEM_BACKUP_GPG_PASSPHRASE_FILE to the passphrase file used for this backup.")
    db_temp = temporary_directory / "restore.postgres.dump"
    workspace_temp = temporary_directory / "restore.actual-workspace.tar.gz"
    command("gpg", "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-file", str(key_file),
            "--decrypt", "--output", str(db_temp), str(encrypted_db))
    command("gpg", "--batch", "--yes", "--pinentry-mode", "loopback", "--passphrase-file", str(key_file),
            "--decrypt", "--output", str(workspace_temp), str(encrypted_workspace))
    db_temp.chmod(0o600)
    workspace_temp.chmod(0o600)
    return db_temp, workspace_temp, True, key_file


def backup(prefix: Path) -> None:
    prefix.parent.mkdir(parents=True, exist_ok=True)
    prefix = prefix.parent.resolve() / prefix.name
    key_file = passphrase_file()
    encrypted = key_file is not None
    db_suffix = ".postgres.dump.gpg" if encrypted else ".postgres.dump"
    workspace_suffix = ".actual-workspace.tar.gz.gpg" if encrypted else ".actual-workspace.tar.gz"
    db_output = Path(f"{prefix}{db_suffix}")
    workspace_output = Path(f"{prefix}{workspace_suffix}")
    conflicts = [Path(f"{prefix}{suffix}") for suffix in (
        ".postgres.dump", ".actual-workspace.tar.gz",
        ".postgres.dump.gpg", ".actual-workspace.tar.gz.gpg",
    )]
    if any(path.exists() for path in conflicts):
        raise RuntimeError("Backup files already exist for this prefix; choose a new prefix or move them first.")

    compose_command = compose()
    api_id = subprocess.check_output([*compose_command, "ps", "--all", "-q", "tandem-api"], text=True).strip()
    if not api_id:
        raise RuntimeError("The API container is unavailable; cannot back up its Actual workspace.")
    api_running = bool(subprocess.check_output([*compose_command, "ps", "--status", "running", "-q", "tandem-api"], text=True).strip())
    db_fd, db_temp_name = tempfile.mkstemp(prefix=f".{prefix.name}.db-", dir=prefix.parent)
    workspace_fd, workspace_temp_name = tempfile.mkstemp(prefix=f".{prefix.name}.workspace-", dir=prefix.parent)
    os.close(db_fd)
    os.close(workspace_fd)
    db_temp = Path(db_temp_name)
    workspace_temp = Path(workspace_temp_name)
    db_temp.chmod(0o600)
    workspace_temp.chmod(0o600)
    marker = prefix.parent / "tandem-backup.last-success"
    success = False
    try:
        if api_running:
            command(*compose_command, "stop", "tandem-api", stdout=subprocess.DEVNULL)
        run_pipe_to_file(
            [*compose_command, "exec", "-T", "tandem-postgres", "sh", "-ec",
             'pg_dump --format=custom --no-owner --username="$POSTGRES_USER" --dbname="$POSTGRES_DB"'],
            db_temp, key_file,
        )
        run_pipe_to_file(
            ["docker", "run", "--rm", "--volumes-from", f"{api_id}:ro", "--entrypoint", "tar",
             NODE_IMAGE, "-czf", "-", "-C", "/data/actual-api", "."],
            workspace_temp, key_file,
        )
        if db_temp.stat().st_size == 0 or workspace_temp.stat().st_size == 0:
            raise RuntimeError("A backup file is empty; the backup was not saved.")
        os.replace(db_temp, db_output)
        os.replace(workspace_temp, workspace_output)
        marker_tmp = marker.with_name(f".{marker.name}.{os.getpid()}.tmp")
        marker_tmp.write_text(
            f"{datetime.now(timezone.utc).strftime('%Y-%m-%dT%H:%M:%SZ')}\n{prefix.name}\n",
            encoding="utf-8",
        )
        marker_tmp.chmod(0o600)
        os.replace(marker_tmp, marker)
        success = True
        if encrypted:
            print("Wrote encrypted PostgreSQL and Actual workspace backups with mode 600. Keep the passphrase separate from the backups.")
        else:
            print("Wrote plaintext PostgreSQL and Actual workspace backups with mode 600; protect them as financial data.")
        print(f"Last successful backup recorded in {marker}")
    finally:
        db_temp.unlink(missing_ok=True)
        workspace_temp.unlink(missing_ok=True)
        restart_failed = False
        if api_running:
            restart = subprocess.run([*compose_command, "start", "tandem-api"], check=False, stdout=subprocess.DEVNULL)
            restart_failed = restart.returncode != 0
        if not success:
            db_output.unlink(missing_ok=True)
            workspace_output.unlink(missing_ok=True)
        if restart_failed:
            raise RuntimeError("The backup completed, but the API could not be restarted.")


def restore(prefix: Path) -> None:
    prefix = prefix.parent.resolve() / prefix.name
    compose_command = compose()
    with tempfile.TemporaryDirectory(prefix="tandem-restore-") as temporary:
        temp_dir = Path(temporary)
        db_input, workspace_input, encrypted, key_file = decrypt_backup(prefix, temp_dir)
        db_input.chmod(0o600)
        workspace_input.chmod(0o600)
        api_id = subprocess.check_output([*compose_command, "ps", "--all", "-q", "tandem-api"], text=True).strip()
        if not api_id:
            raise RuntimeError("The API container must already exist before restore.")
        with db_input.open("rb") as source:
            command(*compose_command, "exec", "-T", "tandem-postgres", "pg_restore", "--list", stdin=source, stdout=subprocess.DEVNULL)
        command("tar", "-tzf", str(workspace_input), stdout=subprocess.DEVNULL)

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
            workspace_restore = r'''set -eu
workspace=/data/actual-api
restore_id="$(cat /proc/sys/kernel/random/uuid)"
staged="$workspace/.restore-new-$restore_id"
previous="$workspace/.restore-previous-$restore_id"
state=preparing
entry_count() {
  count=0
  for entry in "$1"/* "$1"/.[!.]* "$1"/..?*; do
    [ -e "$entry" ] || [ -L "$entry" ] || continue
    [ "$entry" = "$staged" ] && continue
    [ "$entry" = "$previous" ] && continue
    count=$((count + 1))
  done
  printf "%s" "$count"
}
rollback() {
  status=$?
  trap - EXIT
  if [ "$state" = installing ]; then
    for entry in "$workspace"/* "$workspace"/.[!.]* "$workspace"/..?*; do
      [ -e "$entry" ] || [ -L "$entry" ] || continue
      [ "$entry" = "$staged" ] && continue
      [ "$entry" = "$previous" ] && continue
      rm -rf -- "$entry"
    done
  fi
  if [ "$state" != committed ] && [ -d "$previous" ]; then
    for entry in "$previous"/* "$previous"/.[!.]* "$previous"/..?*; do
      [ -e "$entry" ] || [ -L "$entry" ] || continue
      mv -- "$entry" "$workspace/"
    done
  fi
  rm -rf -- "$staged"
  exit "$status"
}
trap rollback EXIT
mkdir -m 700 "$staged"
tar -xzf - --no-same-owner -C "$staged"
expected_count="$(entry_count "$staged")"
[ "$expected_count" -gt 0 ] || { echo "The restored Actual workspace archive is empty." >&2; exit 1; }
mkdir -m 700 "$previous"
state=installing
for entry in "$workspace"/* "$workspace"/.[!.]* "$workspace"/..?*; do
  [ -e "$entry" ] || [ -L "$entry" ] || continue
  [ "$entry" = "$staged" ] && continue
  [ "$entry" = "$previous" ] && continue
  mv -- "$entry" "$previous/"
done
for entry in "$staged"/* "$staged"/.[!.]* "$staged"/..?*; do
  [ -e "$entry" ] || [ -L "$entry" ] || continue
  mv -- "$entry" "$workspace/"
done
actual_count="$(entry_count "$workspace")"
[ "$actual_count" -eq "$expected_count" ] || {
  echo "The restored Actual workspace could not be verified; the old workspace is being put back." >&2
  exit 1
}
state=committed
rm -rf -- "$previous" "$staged"
chown -R node:node "$workspace"
trap - EXIT'''
            with workspace_input.open("rb") as source:
                command("docker", "run", "--rm", "-i", "--volumes-from", api_id, "--entrypoint", "sh",
                        NODE_IMAGE, "-ec", workspace_restore, stdin=source)
        except Exception:
            print("Restore did not complete. Keep the API stopped until you recover the pre-restore state.", file=sys.stderr)
            env = [
                f"TANDEM_COMPOSE_PROJECT={os.environ.get('TANDEM_COMPOSE_PROJECT', 'tandem')}",
                f"TANDEM_ENV_FILE={os.environ.get('TANDEM_ENV_FILE', PROJECT_ROOT / '.env')}",
            ]
            if key_file:
                env.append(f"TANDEM_BACKUP_GPG_PASSPHRASE_FILE={key_file}")
            recovery = [*env, str(Path(__file__).resolve()), "restore", str(safety_prefix)]
            print(f"To recover the pre-restore data, run: {shlex.join(recovery)}", file=sys.stderr)
            raise
    print("Database and Actual workspace restored. Start and verify the API now:")
    print(f"  {shlex.join([*compose_command, 'up', '-d', 'tandem-api'])}")


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
    workspace_plain = Path(f"{prefix}.actual-workspace.tar.gz")
    db_encrypted = Path(f"{db_plain}.gpg")
    workspace_encrypted = Path(f"{workspace_plain}.gpg")
    if db_plain.is_file() and db_plain.stat().st_size and workspace_plain.is_file() and workspace_plain.stat().st_size and not db_encrypted.exists() and not workspace_encrypted.exists():
        kind = "plaintext"
    elif db_encrypted.is_file() and db_encrypted.stat().st_size and workspace_encrypted.is_file() and workspace_encrypted.stat().st_size and not db_plain.exists() and not workspace_plain.exists():
        kind = "encrypted"
    else:
        raise RuntimeError("The last-success marker does not have a complete backup pair.")
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
    backup_parser = subcommands.add_parser("backup", help="write a PostgreSQL and Actual workspace backup")
    backup_parser.add_argument("prefix", type=Path)
    restore_parser = subcommands.add_parser("restore", help="restore a backup pair")
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
