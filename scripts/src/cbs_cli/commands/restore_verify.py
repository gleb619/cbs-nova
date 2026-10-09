"""`restore-verify` command: restore a Postgres dump to a scratch container and assert integrity."""

import argparse
import json
import socket
import time
import uuid
from pathlib import Path
from typing import List, Optional, Tuple

from ..config import Config
from ..printer import Printer
from ..runner import Runner


class RestoreVerifyCommand:
    """Restore a pg_dump archive into a throwaway Postgres container and verify it."""

    IMAGE = "postgres:16"
    SERVICE_MAP = {
        "app": ("nova", "nova"),
        "temporal": ("temporal", "temporal"),
    }
    REQUIRED_TABLES = [
        "dsl_runs",
        "dsl_audit",
        "dsl_events",
        "dsl_api_keys",
    ]
    READY_TIMEOUT = 30
    READY_INTERVAL = 1

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        parser.add_argument(
            "--file",
            default=None,
            help="Path to the .dump file to verify (default: newest ./backups/*.dump)",
        )
        parser.add_argument(
            "--service",
            choices=["app", "temporal"],
            default=None,
            help="Database service the dump belongs to (default: inferred from filename prefix)",
        )
        parser.add_argument(
            "--expect-flyway-version",
            type=int,
            default=None,
            help="Expected max(flyway_schema_history.version) (default: inferred from migrations)",
        )
        parser.add_argument(
            "--json",
            action="store_true",
            help="Emit a JSON verdict instead of human-readable output",
        )

    def run(self, args: argparse.Namespace) -> int:
        self._json = bool(args.json)

        dump_file = self._resolve_dump_file(args.file)
        if dump_file is None:
            return self._finish(False, None, error="no dump file provided and no ./backups/*.dump found")

        service = args.service or self._infer_service(dump_file.name)
        _user, db = self.SERVICE_MAP[service]

        expected_flyway = args.expect_flyway_version or self._infer_flyway_version()
        if expected_flyway is None:
            return self._finish(False, None, error="could not infer expected Flyway version")

        container_name = f"cbs-restore-verify-{uuid.uuid4().hex[:8]}"
        volume_name = f"{container_name}-data"
        port: Optional[int] = None
        started = False
        errors: List[str] = []

        try:
            self._header(f"Restore verify: {dump_file}")

            volume_result = self._docker("volume", "create", volume_name)
            if volume_result.returncode != 0:
                errors.append(f"volume create failed: {volume_result.stderr.strip()}")
                return self._finish(False, port, errors)

            port = self._free_port()
            run_result = self._start_container(container_name, volume_name, db, port)
            if run_result.returncode != 0:
                errors.append(f"container start failed: {run_result.stderr.strip()}")
                return self._finish(False, port, errors)
            started = True

            if not self._wait_for_ready(container_name, db):
                errors.append("scratch Postgres did not become ready in time")
                return self._finish(False, port, errors)
            self._ok(f"scratch Postgres ready on port {port}")

            restore_result = self._restore_dump(container_name, db, dump_file)
            if restore_result.returncode != 0:
                errors.append(f"pg_restore failed: {restore_result.stderr.strip()}")
                return self._finish(False, port, errors)
            self._ok("pg_restore completed")

            tables = self._list_public_tables(container_name, db)
            if tables is None:
                errors.append("could not list restored tables")
                return self._finish(False, port, errors)
            self._ok(f"{len(tables)} tables found")

            missing_tables = [t for t in self.REQUIRED_TABLES if t not in tables]
            if missing_tables:
                errors.append(f"missing required tables: {', '.join(missing_tables)}")
                return self._finish(False, port, errors)

            for table in self.REQUIRED_TABLES:
                count = self._table_row_count(container_name, db, table)
                if count is None:
                    errors.append(f"could not count rows in {table}")
                    return self._finish(False, port, errors)
                if count <= 0:
                    errors.append(f"table {table} has no rows")
                    return self._finish(False, port, errors)
            self._ok("core tables have rows")

            flyway_version = self._flyway_version(container_name, db)
            if flyway_version is None:
                errors.append("could not read flyway_schema_history")
                return self._finish(False, port, errors)
            if flyway_version != expected_flyway:
                errors.append(
                    f"flyway version mismatch: got V{flyway_version}, expected V{expected_flyway}"
                )
                return self._finish(False, port, errors)
            self._ok(f"flyway schema version V{flyway_version}")

            return self._finish(
                True,
                port,
                dump=str(dump_file),
                service=service,
                db=db,
                tables=len(tables),
                flyway_version=flyway_version,
            )

        except Exception as exc:  # noqa: BLE001
            errors.append(f"unexpected error: {exc}")
            return self._finish(False, port, errors)

        finally:
            self._docker("rm", "-f", container_name)
            self._docker("volume", "rm", volume_name)

    def _resolve_dump_file(self, file_arg: Optional[str]) -> Optional[Path]:
        if file_arg:
            path = Path(file_arg).expanduser().resolve()
            if not path.exists():
                self._fail(f"dump file not found: {path}")
                return None
            return path

        backup_dir = Config.ROOT_DIR / "backups"
        dumps = sorted(backup_dir.glob("*.dump"), key=lambda p: p.stat().st_mtime, reverse=True)
        if not dumps:
            self._fail(f"no .dump files found in {backup_dir}")
            return None
        return dumps[0]

    @staticmethod
    def _infer_service(filename: str) -> str:
        if filename.startswith("temporal-"):
            return "temporal"
        return "app"

    @classmethod
    def _infer_flyway_version(cls) -> Optional[int]:
        migration_dir = (
            Config.ROOT_DIR
            / "backend"
            / "dsl-starter"
            / "starter"
            / "src"
            / "main"
            / "resources"
            / "db"
            / "migration"
            / "postgres"
        )
        versions = []
        for path in migration_dir.glob("V*__*.sql"):
            prefix = path.name.split("__", 1)[0]
            if prefix.startswith("V"):
                try:
                    versions.append(int(prefix[1:]))
                except ValueError:
                    continue
        return max(versions) if versions else None

    @staticmethod
    def _free_port() -> int:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as sock:
            sock.bind(("127.0.0.1", 0))
            return int(sock.getsockname()[1])

    def _start_container(
        self,
        container_name: str,
        volume_name: str,
        db: str,
        port: int,
    ) -> "subprocess.CompletedProcess[str]":
        return self._docker(
            "run",
            "-d",
            "--name",
            container_name,
            "-e",
            "POSTGRES_USER=postgres",
            "-e",
            "POSTGRES_PASSWORD=postgres",
            "-e",
            f"POSTGRES_DB={db}",
            "-p",
            f"{port}:5432",
            "-v",
            f"{volume_name}:/var/lib/postgresql/data",
            self.IMAGE,
        )

    def _wait_for_ready(self, container_name: str, db: str) -> bool:
        deadline = time.time() + self.READY_TIMEOUT
        while time.time() < deadline:
            result = self._docker_exec(container_name, "pg_isready", "-U", "postgres", "-d", db)
            if result.returncode == 0:
                return True
            time.sleep(self.READY_INTERVAL)
        return False

    def _restore_dump(
        self,
        container_name: str,
        db: str,
        dump_file: Path,
    ) -> "subprocess.CompletedProcess[bytes]":
        return Runner.run(
            [
                "docker",
                "exec",
                "-i",
                "-T",
                container_name,
                "pg_restore",
                "-U",
                "postgres",
                "-d",
                db,
                "-Fc",
                "--no-owner",
                "--no-privileges",
            ],
            input=dump_file.read_bytes(),
            capture=True,
            check=False,
            text=False,
        )

    def _list_public_tables(self, container_name: str, db: str) -> Optional[List[str]]:
        sql = (
            "SELECT table_name FROM information_schema.tables "
            "WHERE table_schema = 'public' ORDER BY table_name;"
        )
        result = self._psql(container_name, db, sql)
        if result.returncode != 0:
            return None
        return [line.strip() for line in result.stdout.splitlines() if line.strip()]

    def _table_row_count(self, container_name: str, db: str, table: str) -> Optional[int]:
        result = self._psql(container_name, db, f"SELECT count(*) FROM {table};")
        if result.returncode != 0:
            return None
        try:
            return int(result.stdout.strip())
        except ValueError:
            return None

    def _flyway_version(self, container_name: str, db: str) -> Optional[int]:
        sql = "SELECT COALESCE(max(version), 0) FROM flyway_schema_history;"
        result = self._psql(container_name, db, sql)
        if result.returncode != 0:
            return None
        try:
            return int(result.stdout.strip())
        except ValueError:
            return None

    def _psql(
        self,
        container_name: str,
        db: str,
        sql: str,
    ) -> "subprocess.CompletedProcess[str]":
        return self._docker_exec(container_name, "psql", "-U", "postgres", "-d", db, "-Atc", sql)

    @classmethod
    def _docker(cls, *args: str) -> "subprocess.CompletedProcess[str]":
        return Runner.run(["docker", *args], capture=True, check=False, text=True)

    @classmethod
    def _docker_exec(cls, container_name: str, *args: str) -> "subprocess.CompletedProcess[str]":
        return cls._docker("exec", "-T", container_name, *args)

    def _header(self, msg: str) -> None:
        if not self._json:
            Printer.header(msg)

    def _ok(self, msg: str) -> None:
        if not self._json:
            Printer.ok(msg)

    def _fail(self, msg: str) -> None:
        if not self._json:
            Printer.fail(msg)

    def _finish(
        self,
        ok: bool,
        port: Optional[int],
        errors: Optional[List[str]] = None,
        **kwargs,
    ) -> int:
        errors = errors or []

        if self._json:
            payload = {"ok": ok, "port": port, "errors": errors, **kwargs}
            print(json.dumps(payload, default=str))
            return 0 if ok else 1

        for error in errors:
            Printer.fail(error)

        if ok:
            tables = kwargs.get("tables", 0)
            flyway = kwargs.get("flyway_version")
            print(f"RESTORE VERIFY OK — {tables} tables, flyway V{flyway}")
            return 0

        print("RESTORE VERIFY FAILED")
        return 1
