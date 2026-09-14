import argparse
import io
from contextlib import redirect_stdout
from pathlib import Path

from ..config import Config
from ..printer import Printer
from .openapi_diff import OpenApiDiffCommand


class OpenApiDiffTestCommand:
    """Run the T419 OpenAPI additive vs breaking-change classifier self-test."""

    FIXTURES: Path = Config.ROOT_DIR / "scripts" / "test" / "fixtures"

    def run(self, args: argparse.Namespace) -> int:
        cases = [
            ("additive only -> exit 0", "old-additive.json", "new-additive.json", 0),
            ("breaking + same info.version -> exit 2", "old-breaking.json", "new-breaking-same-version.json", 2),
            ("breaking + bumped info.version -> exit 0", "old-breaking.json", "new-breaking-bumped-version.json", 0),
        ]

        fails = 0
        for name, old_name, new_name, want in cases:
            ns = argparse.Namespace(
                old_path=str(self.FIXTURES / old_name),
                new_path=str(self.FIXTURES / new_name),
            )
            buf = io.StringIO()
            with redirect_stdout(buf):
                got = OpenApiDiffCommand().run(ns)

            if got == want:
                Printer.ok(f"{name} (exit={got})")
            else:
                Printer.fail(f"{name} (got exit={got}, want {want})")
                fails += 1

        if fails:
            print(f"\n{fails} classifier case(s) failed.")
            return 1
        print("\nAll classifier self-test cases passed.")
        return 0
