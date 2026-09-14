#!/usr/bin/env python3
"""cbs-cli — single Python orchestrator for the cbs-nova local dev stack."""

import sys
from pathlib import Path

# Local package with split command classes lives next to this script.
sys.path.insert(0, str(Path(__file__).resolve().parent / "src"))

from cbs_cli.cli import CLI  # noqa: E402


def main() -> int:
    return CLI().run()


if __name__ == "__main__":
    sys.exit(main())
