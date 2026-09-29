import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.lint_docs_links import check  # noqa: E402


def _write_docs(tmp_path: Path, files: dict[str, str]) -> Path:
    """Write a docs/ tree; keys are paths relative to docs/."""
    docs = tmp_path / "docs"
    docs.mkdir(parents=True, exist_ok=True)
    for rel, body in files.items():
        p = docs / rel
        p.parent.mkdir(parents=True, exist_ok=True)
        p.write_text(body)
    return docs


class TestCheck:
    def test_ok_link_passes(self, tmp_path, capsys):
        _write_docs(
            tmp_path,
            {
                "index.md": "# Index\n\nSee [architecture](architecture.md).\n",
                "architecture.md": "# Architecture\n\nIntro.\n",
            },
        )
        assert check(tmp_path) == 0
        out = capsys.readouterr().out
        assert "all relative links resolve" in out

    def test_broken_link_fails(self, tmp_path, capsys):
        _write_docs(
            tmp_path,
            {
                "index.md": "# Index\n\nSee [missing](does-not-exist.md).\n",
            },
        )
        assert check(tmp_path) == 1
        out = capsys.readouterr().out
        assert "1 broken relative link" in out
        assert "index.md" in out
        assert "does-not-exist.md" in out

    def test_anchor_only_link_not_flagged(self, tmp_path, capsys):
        """A bare `#section` (no path) is not a file target — must be ignored."""
        _write_docs(
            tmp_path,
            {
                "index.md": "# Index\n\nJump to [section](#section).\n",
            },
        )
        assert check(tmp_path) == 0
        out = capsys.readouterr().out
        assert "all relative links resolve" in out

    def test_link_in_fenced_code_block_skipped(self, tmp_path, capsys):
        """Links inside ```...``` blocks must NOT trigger a broken-link report."""
        _write_docs(
            tmp_path,
            {
                "index.md": (
                    "# Index\n\n"
                    "Real link: [ok](architecture.md).\n\n"
                    "```\n"
                    "ignored link: [example](does-not-exist-in-code.md)\n"
                    "```\n"
                ),
                "architecture.md": "# Architecture\n",
            },
        )
        assert check(tmp_path) == 0
        out = capsys.readouterr().out
        assert "all relative links resolve" in out
        assert "does-not-exist-in-code.md" not in out

    def test_skip_plans_and_adr_dirs(self, tmp_path, capsys):
        """Links inside docs/plans/ and docs/adr/ must not be scanned."""
        _write_docs(
            tmp_path,
            {
                "index.md": "# Index\n\nSee [plan](plans/T999-fictional.md).\n",
                "plans/T999-fictional.md": "# fictional plan (ignored by scanner)\n",
            },
        )
        assert check(tmp_path) == 0

    def test_http_and_absolute_links_skipped(self, tmp_path, capsys):
        """External and absolute links are out of scope."""
        _write_docs(
            tmp_path,
            {
                "index.md": (
                    "# Index\n\n"
                    "[external](https://example.com/page.md)\n"
                    "[absolute](/some/other/path.md)\n"
                    "[email](mailto:test@example.com)\n"
                ),
            },
        )
        assert check(tmp_path) == 0

    def test_nested_link_resolves(self, tmp_path, capsys):
        """A `../` link from a subdirectory should resolve correctly."""
        _write_docs(
            tmp_path,
            {
                "index.md": "# Top\n",
                "sub/page.md": (
                    "# Subpage\n\n"
                    "See [top](../index.md).\n"
                    "See [sibling](./other.md).\n"
                ),
                "sub/other.md": "# Other\n",
            },
        )
        assert check(tmp_path) == 0

    def test_broken_link_in_subdir_reports_path(self, tmp_path, capsys):
        _write_docs(
            tmp_path,
            {
                "index.md": "# Top\n",
                "sub/page.md": "See [missing](../nope.md).\n",
            },
        )
        assert check(tmp_path) == 1
        out = capsys.readouterr().out
        assert "sub/page.md" in out
        assert "../nope.md" in out