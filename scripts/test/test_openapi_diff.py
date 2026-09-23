import argparse
import io
import sys
from contextlib import redirect_stdout
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.openapi_diff import OpenApiDiffCommand  # noqa: E402
from cbs_cli.config import Config  # noqa: E402


FIXTURES: Path = Config.ROOT_DIR / "scripts" / "test" / "fixtures"


def _run(old_name: str, new_name: str) -> int:
    ns = argparse.Namespace(
        old_path=str(FIXTURES / old_name),
        new_path=str(FIXTURES / new_name),
    )
    buf = io.StringIO()
    with redirect_stdout(buf):
        return OpenApiDiffCommand().run(ns)


def test_additive_only_exits_zero():
    assert _run("old-additive.json", "new-additive.json") == 0


def test_breaking_with_same_info_version_exits_two():
    assert _run("old-breaking.json", "new-breaking-same-version.json") == 2


def test_breaking_with_bumped_info_version_exits_zero():
    assert _run("old-breaking.json", "new-breaking-bumped-version.json") == 0
