import argparse
import json
import os
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Dict, List, Tuple

from ..config import Config
from ..docker import Docker
from ..printer import Printer
from ..runner import Runner


class DbCommand:
    SERVICE_MAP: Dict[str, Tuple[str, str]] = {
        "app": ("nova", "nova"),
        "temporal": ("temporal", "temporal"),
    }

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        sub = parser.add_subparsers(dest="db_action")

        p = sub.add_parser("backup", help="Back up Postgres databases via pg_dump -Fc")
        p.add_argument("--out", default="./backups/", help="Output directory (default: ./backups/)")
        p.add_argument(
            "--services",
            default="app,temporal",
            help="Comma-separated services to back up (default: app,temporal)",
        )

        p = sub.add_parser("restore", help="Restore a Postgres database from a pg_dump archive")
        p.add_argument("--file", required=True, help="Path to the .dump file to restore")
        p.add_argument(
            "--service",
            default="app",
            choices=["app", "temporal"],
            help="Service/database to restore (default: app)",
        )
        p.add_argument("--yes", action="store_true", help="Confirm destructive restore")

    def run(self, args: argparse.Namespace) -> int:
        action = getattr(args, "db_action", None)
        if action == "backup":
            return self._backup(args)
        if action == "restore":
            return self._restore(args)
        Printer.fail("unknown db subcommand")
        return 1

    def _backup(self, args: argparse.Namespace) -> int:
        out_dir = Path(args.out).expanduser().resolve()
        out_dir.mkdir(parents=True, exist_ok=True)

        services = [s.strip() for s in args.services.split(",") if s.strip()]
        timestamp = datetime.now().strftime("%Y%m%d-%H%M%S")

        Printer.header(f"Backing up services: {', '.join(services)}")
        print()

        for service in services:
            if service not in self.SERVICE_MAP:
                Printer.fail(f"unknown service '{service}'")
                return 1
            user, db = self.SERVICE_MAP[service]
            dump_path = out_dir / f"{service}-{timestamp}.dump"

            result = Docker.run(
                "exec",
                "-T",
                "postgres",
                "pg_dump",
                "-U",
                user,
                "-d",
                db,
                "-Fc",
                capture=True,
                check=False,
                text=False,
            )
            if result.returncode != 0:
                Printer.fail(f"pg_dump failed for {service}: {result.stderr.strip()}")
                return 1

            dump_path.write_bytes(result.stdout.encode() if isinstance(result.stdout, str) else result.stdout)
            Printer.ok(f"{service} -> {dump_path}")

        print()
        self._print_vhs_tape_commands(out_dir, timestamp)
        return 0

    def _restore(self, args: argparse.Namespace) -> int:
        dump_file = Path(args.file).expanduser().resolve()
        if not dump_file.exists():
            Printer.fail(f"dump file not found: {dump_file}")
            return 1

        user, db = self.SERVICE_MAP[args.service]

        if not args.yes:
            print(f"This will overwrite the '{db}' database. Re-run with --yes to proceed.")
            return 1

        Printer.header(f"Restoring {args.service} database '{db}' from {dump_file}")

        result = Runner.run(
            Docker._cmd(
                "exec",
                "-T",
                "postgres",
                "pg_restore",
                "-U",
                "postgres",
                "-d",
                db,
                "-Fc",
                "--clean",
                "--if-exists",
            ),
            input=dump_file.read_bytes(),
            capture=False,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail(f"pg_restore failed for {args.service}")
            return 1
        Printer.ok(f"restored {args.service} from {dump_file}")
        return 0

    def _print_vhs_tape_commands(self, out_dir: Path, timestamp: str) -> None:
        volumes = DbCommand._find_vhs_volumes()
        if not volumes:
            Printer.skip("No compose VHS tape volumes detected; local VHS tapes are not persisted unless a bind/named volume is mounted.")
            return

        Printer.header("VHS tape volumes detected — copy them separately with:")
        for service, container_path, volume_name in volumes:
            archive = out_dir / f"{service}-vhs-{timestamp}.tgz"
            print(
                f"  docker run --rm -v {volume_name}:/vhs-data -v {out_dir}:/backups "
                f"busybox tar -czf /backups/{archive.name} -C /vhs-data ."
            )

    @staticmethod
    def _find_vhs_volumes() -> List[Tuple[str, str, str]]:
        result = Docker.run("config", "--format", "json", capture=True, check=False)
        if result.returncode != 0 or not result.stdout:
            return []
        try:
            config = json.loads(result.stdout)
        except json.JSONDecodeError:
            return []

        services = config.get("services", {})
        found: List[Tuple[str, str, str]] = []
        for service_name, service in services.items():
            mounts = service.get("volumes", [])
            for mount in mounts:
                if isinstance(mount, str):
                    # short form: "<volume>:<path>" or "<volume>:<path>:<mode>"
                    parts = mount.split(":")
                    if len(parts) >= 2:
                        volume_name, container_path = parts[0], parts[1]
                        if DbCommand._looks_like_vhs(container_path, volume_name):
                            found.append((service_name, container_path, volume_name))
                elif isinstance(mount, dict):
                    volume_name = mount.get("source", "")
                    container_path = mount.get("target", "")
                    if DbCommand._looks_like_vhs(container_path, volume_name):
                        found.append((service_name, container_path, volume_name))
        return found

    @staticmethod
    def _looks_like_vhs(container_path: str, volume_name: str) -> bool:
        return bool(re.search(r"vhs", container_path, re.IGNORECASE)) or bool(
            re.search(r"vhs", volume_name, re.IGNORECASE)
        )
