import argparse
import json
import os
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional, Tuple

from ..config import Config
from ..curl import Curl
from ..printer import Printer
from ..runner import Runner


class VhsCommand:
    BASE_ENV_VARS = ["CBS_VHS_API_URL"]

    def __init__(self) -> None:
        self.base_url: str = ""

    def run(self, args: argparse.Namespace) -> int:
        self.base_url = self._resolve_base_url(args)
        action = getattr(args, "vhs_action", None)
        if action == "list":
            return self._list(args)
        if action == "download":
            return self._download(args)
        if action == "delete":
            return self._delete(args)
        if action == "replay":
            return self._replay(args)
        if action == "loadtest":
            return self._loadtest(args)
        Printer.fail("unknown vhs subcommand")
        return 1

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        sub = parser.add_subparsers(dest="vhs_action")

        p = sub.add_parser("list", help="List recorded VHS tapes")
        p.add_argument("--url", help="VHS API base URL (default: BFF_BASE_URL/BACKEND_BASE_URL)")
        p.add_argument("--route", help="Filter by route name")
        p.add_argument("--since", help="ISO-8601 lower bound on recorded_at")
        p.add_argument("--limit", type=int, default=50, help="Maximum tapes to show")

        p = sub.add_parser("download", help="Download a tape by runId/correlationId/filename")
        p.add_argument("--url", help="VHS API base URL (default: BFF_BASE_URL/BACKEND_BASE_URL)")
        p.add_argument("identifier", help="runId, correlationId, or filename of the tape")
        p.add_argument("-o", "--output", help="Output file (default: stdout)")

        p = sub.add_parser("delete", help="Delete a tape by runId or filename")
        p.add_argument("--url", help="VHS API base URL (default: BFF_BASE_URL/BACKEND_BASE_URL)")
        p.add_argument("identifier", help="runId or filename of the tape")

        p = sub.add_parser("replay", help="Replay a tape")
        p.add_argument("--url", help="VHS API base URL (default: BFF_BASE_URL/BACKEND_BASE_URL)")
        p.add_argument("identifier", help="runId or filename of the tape to replay")
        p.add_argument("--target", default="dry-run", help="Replay target (dry-run|local)")
        p.add_argument("--mode", default="exact", help="Replay mode (exact|load)")
        p.add_argument("--speed", type=float, default=1.0, help="Replay speed multiplier")
        p.add_argument("--concurrency", type=int, default=1, help="Load replay concurrency")
        p.add_argument("--copies", type=int, default=1, help="Load replay copies")

        p = sub.add_parser("loadtest", help="Run a VHS load test against multiple tapes")
        p.add_argument("--url", help="VHS API base URL (default: BFF_BASE_URL/BACKEND_BASE_URL)")
        p.add_argument("--tapes", default="**/*.vhs.jsonl", help="Glob pattern for tape files")
        p.add_argument("--target", default="dry-run", help="Replay target (dry-run|local)")
        p.add_argument("--speed", type=float, default=1.0, help="Replay speed multiplier")
        p.add_argument("--concurrency", type=int, default=4, help="Concurrency cap")
        p.add_argument("--duration", type=int, default=0, help="Duration cap in seconds (0=no cap)")

    def _resolve_base_url(self, args: argparse.Namespace) -> str:
        raw = getattr(args, "url", None) or self._first_env(*self.BASE_ENV_VARS)
        if raw:
            return raw.rstrip("/")
        if Config.BFF_BASE_URL:
            return Config.BFF_BASE_URL.rstrip("/")
        return Config.BACKEND_BASE_URL.rstrip("/")

    @staticmethod
    def _first_env(*names: str) -> Optional[str]:
        import os

        for name in names:
            value = os.environ.get(name)
            if value:
                return value
        return None

    def _list(self, args: argparse.Namespace) -> int:
        url = f"{self.base_url}/api/v1/vhs/tapes"
        query: List[Tuple[str, str]] = []
        if args.route:
            query.append(("route", args.route))
        if args.since:
            query.append(("since", args.since))
        if args.limit:
            query.append(("limit", str(args.limit)))
        if query:
            url += "?" + "&".join(f"{k}={self._escape(v)}" for k, v in query)

        body = Curl.get(url, timeout=10, fail=False)
        if body is None:
            Printer.fail(f"could not list tapes from {url}")
            return 1
        try:
            data = json.loads(body)
            items = data.get("items", data) if isinstance(data, dict) else data
        except json.JSONDecodeError:
            Printer.fail("backend returned non-JSON response")
            return 1

        if not isinstance(items, list) or not items:
            print("No tapes found.")
            return 0

        rows = [self._format_summary(item) for item in items]
        headers = ["filename", "recorded_at", "run_id", "correlation_id", "route", "size", "event_count"]
        widths = [max(len(headers[i]), max((len(r[i]) for r in rows), default=0)) for i in range(len(headers))]
        print("  ".join(headers[i].ljust(widths[i]) for i in range(len(headers))))
        print("  ".join("-" * widths[i] for i in range(len(headers))))
        for row in rows:
            print("  ".join(row[i].ljust(widths[i]) for i in range(len(headers))))
        return 0

    def _download(self, args: argparse.Namespace) -> int:
        run_id = self._resolve_run_id(args.identifier)
        if run_id is None:
            Printer.fail(f"no tape matches identifier '{args.identifier}'")
            return 1
        url = f"{self.base_url}/api/v1/vhs/tapes/{self._escape_path(run_id)}"
        if args.output:
            output = Path(args.output)
            result = Runner.run(
                ["curl", "--silent", "--show-error", "--fail", "--max-time", "30", "-o", str(output), url],
                capture=False,
                check=False,
            )
            if result.returncode != 0:
                Printer.fail(f"download failed for {run_id}")
                return 1
            Printer.ok(f"downloaded {run_id} to {output}")
            return 0

        result = Runner.run(
            ["curl", "--silent", "--show-error", "--fail", "--max-time", "30", url],
            capture=False,
            check=False,
        )
        return 0 if result.returncode == 0 else 1

    def _delete(self, args: argparse.Namespace) -> int:
        run_id = self._resolve_run_id(args.identifier)
        if run_id is None:
            Printer.fail(f"no tape matches identifier '{args.identifier}'")
            return 1
        url = f"{self.base_url}/api/v1/vhs/tapes/{self._escape_path(run_id)}"
        result = Runner.run(
            ["curl", "--silent", "--show-error", "--fail", "--max-time", "10", "-X", "DELETE", url],
            capture=True,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail(f"delete failed for {run_id}")
            return 1
        Printer.ok(f"deleted {run_id}")
        return 0

    def _replay(self, args: argparse.Namespace) -> int:
        run_id = self._resolve_run_id(args.identifier)
        if run_id is None:
            Printer.fail(f"no tape matches identifier '{args.identifier}'")
            return 1
        body = json.dumps({
            "target": args.target,
            "mode": args.mode,
            "speed": args.speed,
            "concurrency": args.concurrency,
            "copies": args.copies,
        })
        url = f"{self.base_url}/api/v1/vhs/tapes/{self._escape_path(run_id)}/replay"
        result = Runner.run(
            ["curl", "--silent", "--show-error", "--fail", "--max-time", "60", "-X", "POST",
             "-H", "Content-Type: application/json", "-d", body, url],
            capture=True,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail(f"replay failed for {run_id}")
            if result.stdout:
                print(result.stdout)
            return 1
        print(result.stdout)
        return 0

    def _loadtest(self, args: argparse.Namespace) -> int:
        target = args.target
        speed = args.speed
        concurrency = args.concurrency
        duration = args.duration
        tapes = args.tapes

        # Production guard: warn loudly
        is_production = target not in ("dry-run", "dryrun", "local", "")
        if is_production:
            allow_config = os.environ.get("CBS_VHS_REPLAY_ALLOW_PRODUCTION", "")
            allow_env = os.environ.get("CBS_VHS_REPLAY_ALLOW_PRODUCTION", "")
            if not (allow_config and allow_env):
                Printer.fail(
                    f"Refusing load-test against production-like target '{target}'. "
                    "Set BOTH cbs.vhs.replay.allow-production=true AND "
                    "CBS_VHS_REPLAY_ALLOW_PRODUCTION=1"
                )
                return 1

        Printer.header(
            f"VHS load-test: target={target} speed={speed} concurrency={concurrency} "
            f"duration={duration}s tapes={tapes}"
        )

        body = json.dumps({
            "tapes": tapes,
            "target": target,
            "speed": speed,
            "concurrency": concurrency,
            "duration": duration * 1000 if duration else 0,
        })
        url = f"{self.base_url}/api/v1/vhs/loadtest"
        result = Runner.run(
            ["curl", "--silent", "--show-error", "--fail", "--max-time", "300", "-X", "POST",
             "-H", "Content-Type: application/json", "-d", body, url],
            capture=True,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail("load-test request failed")
            if result.stdout:
                print(result.stdout)
            return 1

        try:
            report = json.loads(result.stdout)
        except json.JSONDecodeError:
            print(result.stdout)
            return 0

        self._print_loadtest_report(report)
        return 0

    @staticmethod
    def _print_loadtest_report(report: Dict[str, Any]) -> None:
        target = report.get("target", "?")
        tapes_loaded = report.get("tapesLoaded", 0)
        total_calls = report.get("totalCalls", 0)
        successful = report.get("successfulCalls", 0)
        failed = report.get("failedCalls", 0)
        wall_ms = report.get("wallClockMs", 0)

        print(f"\n  target: {target}  tapes: {tapes_loaded}  "
              f"calls: {total_calls}  success: {successful}  failed: {failed}  "
              f"wall: {wall_ms}ms")

        summaries = report.get("tapeSummaries", [])
        if not summaries:
            print("  No per-tape summaries.")
            return

        headers = ["tape", "calls", "succ", "fail", "p50", "p95", "p99", "err%", "total"]
        rows = []
        for s in summaries:
            rows.append((
                str(s.get("tapeName", "?"))[:40],
                str(s.get("totalCalls", 0)),
                str(s.get("successfulCalls", 0)),
                str(s.get("failedCalls", 0)),
                f"{s.get('p50Ms', 0)}ms",
                f"{s.get('p95Ms', 0)}ms",
                f"{s.get('p99Ms', 0)}ms",
                f"{s.get('errorRatePct', 0):.1f}%",
                str(s.get("totalCalls", 0)),
            ))

        widths = [max(len(headers[i]), max((len(r[i]) for r in rows), default=0)) for i in range(len(headers))]
        print()
        print("  " + "  ".join(headers[i].ljust(widths[i]) for i in range(len(headers))))
        print("  " + "  ".join("-" * widths[i] for i in range(len(headers))))
        for row in rows:
            print("  " + "  ".join(row[i].ljust(widths[i]) for i in range(len(headers))))
        print()

    def _resolve_run_id(self, identifier: str) -> Optional[str]:
        if "/" in identifier or identifier.endswith(".vhs.jsonl"):
            return self._run_id_from_filename(Path(identifier).name)
        # Treat as runId first; fall back to correlation id or filename.
        body = Curl.get(f"{self.base_url}/api/v1/vhs/tapes?limit=1000", timeout=10, fail=False)
        if not body:
            return identifier if "/" not in identifier else None
        try:
            data = json.loads(body)
            items = data.get("items", data) if isinstance(data, dict) else data
            if not isinstance(items, list):
                return identifier
            for item in items:
                if not isinstance(item, dict):
                    continue
                if item.get("run_id") == identifier or item.get("filename") == identifier:
                    return item.get("run_id")
                if item.get("correlation_id") == identifier:
                    return item.get("run_id")
        except json.JSONDecodeError:
            pass
        return identifier if "/" not in identifier else None

    @staticmethod
    def _run_id_from_filename(filename: str) -> Optional[str]:
        # Format: <timestamp>_<runId>_<correlationId>.vhs.jsonl
        if not filename.endswith(".vhs.jsonl"):
            return None
        core = filename[: -len(".vhs.jsonl")]
        parts = core.split("_", 2)
        if len(parts) >= 2:
            return parts[1]
        return None

    @staticmethod
    def _format_summary(item: Dict[str, Any]) -> List[str]:
        return [
            str(item.get("filename", "")),
            str(item.get("recorded_at", "")),
            str(item.get("run_id", "")),
            str(item.get("correlation_id", "") or ""),
            str(item.get("route", "")),
            str(item.get("size_bytes", item.get("size", 0))),
            str(item.get("event_count", 0)),
        ]

    @staticmethod
    def _escape(value: str) -> str:
        import urllib.parse
        return urllib.parse.quote(value, safe="")

    @staticmethod
    def _escape_path(value: str) -> str:
        import urllib.parse
        return urllib.parse.quote(value, safe="/")
