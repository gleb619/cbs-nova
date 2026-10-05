"""`runbook-verify` command: execute the read-only curl examples in docs/runbook.md."""

import argparse
from pathlib import Path

from ..config import Config
from ..curl import Curl
from ..printer import Printer
from .runbook_verify_parser import CurlExample, extract_curl_examples, rewrite_url


class RunbookVerifyCommand:
    """Verify read-only curl examples from the operator runbook."""

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        parser.add_argument(
            "--file",
            default=str(Config.ROOT_DIR / "docs" / "runbook.md"),
            help="path to the runbook markdown file",
        )
        parser.add_argument(
            "--backend-url",
            default=Config.BACKEND_BASE_URL,
            help="backend base URL to use instead of http://localhost:8090",
        )
        parser.add_argument(
            "--bff-url",
            default=Config.BFF_BASE_URL,
            help="BFF base URL to use instead of http://localhost:3000",
        )
        parser.add_argument(
            "--include-writes",
            action="store_true",
            help="also execute mutating requests (default: skip them)",
        )
        parser.add_argument(
            "--timeout",
            type=int,
            default=5,
            help="per-request timeout in seconds",
        )

    def run(self, args: argparse.Namespace) -> int:
        runbook = Path(args.file)
        if not runbook.exists():
            Printer.fail(f"runbook not found: {runbook}")
            return 1

        backend_url = args.backend_url.rstrip("/")
        bff_url = args.bff_url.rstrip("/")

        examples = list(
            extract_curl_examples(
                runbook.read_text(encoding="utf-8"),
                include_writes=args.include_writes,
            )
        )
        if not examples:
            Printer.header("No curl examples found.")
            return 0

        Printer.header(f"Verifying {len(examples)} curl example(s) from {runbook}...")
        print()

        fails = 0
        for example in examples:
            if example.skip_reason:
                Printer.skip(self._format_message(example, example.skip_reason))
                continue

            target_url = rewrite_url(example.url, backend_url, bff_url)
            status = Curl.status_code(target_url, timeout=args.timeout)

            if status is None:
                Printer.fail(self._format_message(example, "unreachable"))
                fails += 1
            elif status == "404":
                Printer.fail(self._format_message(example, "HTTP 404"))
                fails += 1
            elif status.startswith("2"):
                Printer.ok(self._format_message(example, f"HTTP {status}"))
            else:
                Printer.fail(self._format_message(example, f"HTTP {status}"))
                fails += 1

        print()
        if fails:
            print(f"{fails} runnable example(s) failed.")
            return 1
        print("All runnable examples passed.")
        return 0

    @classmethod
    def _extract_curl_examples(cls, text: str, include_writes: bool = False):
        """Thin wrapper kept for test compatibility."""
        return extract_curl_examples(text, include_writes=include_writes)

    @classmethod
    def _parse_command(cls, text: str, line_number: int, include_writes: bool = False):
        """Thin wrapper kept for test compatibility."""
        from .runbook_verify_parser import _parse_command

        return _parse_command(text, line_number, include_writes=include_writes)

    @staticmethod
    def _format_message(example: CurlExample, detail: str) -> str:
        path = example.url or ""
        if example.line_number:
            return f"{example.method} {path} (line {example.line_number}) — {detail}"
        return f"{example.method} {path} — {detail}"
