import argparse
import re
from pathlib import Path

from ..config import Config
from ..printer import Printer

# Matches the markdown link target: ](path)(#anchor)?
# Captures (1) the path, (2) optional anchor. Skips whitespace inside parens, which is fine for our docs.
LINK_RE = re.compile(r"\]\(([^)#\s]+)(#[^)]*)?\)")

# Directory names (relative to docs/) whose files are skipped from the scan.
SKIP_DIRS = {"plans", "adr"}

# Link prefixes that are not relative file paths.
SKIP_PREFIXES = ("http://", "https://", "mailto:", "/")

# Fenced code block delimiter. We treat ```...``` and ~~~...~~~ the same way.
FENCE_RE = re.compile(r"^(```+|~~~+)")


def _in_fenced(lines: list[str], line_idx: int) -> bool:
    """Return True if line lines[line_idx] falls inside an open ```/~~~ fenced block."""
    in_block = False
    fence_char: str | None = None
    fence_len: int | None = None
    for i in range(line_idx):
        m = FENCE_RE.match(lines[i].lstrip())
        if not in_block:
            if m and (fence_char is None or m.group(1)[0] == fence_char):
                fence_char = m.group(1)[0]
                fence_len = len(m.group(1))
                in_block = True
        else:
            if m and m.group(1)[0] == fence_char and len(m.group(1)) >= fence_len:
                in_block = False
                fence_char = None
                fence_len = None
    # Re-evaluate: also check if THIS line is itself a fence closer; we want
    # to skip only links *inside* the block, so if line_idx points at the
    # opening fence line, that line is itself considered inside (no link
    # will match there in practice because fence markers don't contain
    # markdown links). We use the inclusive test below.
    in_block_now = False
    fence_char = None
    fence_len = None
    for i in range(line_idx + 1):
        m = FENCE_RE.match(lines[i].lstrip())
        if not in_block_now:
            if m and (fence_char is None or m.group(1)[0] == fence_char):
                fence_char = m.group(1)[0]
                fence_len = len(m.group(1))
                in_block_now = True
        else:
            if m and m.group(1)[0] == fence_char and len(m.group(1)) >= fence_len:
                in_block_now = False
                fence_char = None
                fence_len = None
    return in_block_now


def _iter_markdown_files(root: Path):
    """Yield every docs/**/*.md file, skipping plans/ and adr/ subtrees."""
    if not root.exists():
        return
    for path in sorted(root.rglob("*.md")):
        if any(part in SKIP_DIRS for part in path.relative_to(root).parts):
            continue
        yield path


def _scan(root: Path) -> list[tuple[Path, str, int]]:
    """Return a list of (file, target, line_no) for every broken relative link.

    line_no is the line number in the original file (1-indexed)."""
    broken: list[tuple[Path, str, int]] = []
    for path in _iter_markdown_files(root):
        text = path.read_text()
        lines = text.splitlines()
        for line_no, line in enumerate(lines, start=1):
            if _in_fenced(lines, line_no - 1):
                continue
            for m in LINK_RE.finditer(line):
                target = m.group(1)
                if target.startswith(SKIP_PREFIXES):
                    continue
                resolved = (path.parent / target).resolve()
                if not resolved.exists():
                    broken.append((path, target, line_no))
    return broken


def check(root: Path) -> int:
    """Check docs/**/*.md for broken relative links. Returns 0 if clean, 1 if any broken."""
    docs_dir = root / "docs"
    Printer.header(f"Linting relative markdown links under {docs_dir}...")
    broken = _scan(docs_dir)
    if not broken:
        Printer.ok("all relative links resolve")
        return 0
    Printer.fail(f"{len(broken)} broken relative link(s):")
    for path, target, line_no in broken:
        rel = path.relative_to(root)
        print(f"  - {rel}:{line_no} → {target}")
    return 1


class LintDocsLinksCommand:
    """Lint docs/**/*.md relative markdown links. Exits non-zero listing every broken link."""

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        parser.add_argument(
            "--root",
            default=None,
            help="repo root (default: auto-detected via Config.ROOT_DIR)",
        )

    def run(self, args: argparse.Namespace) -> int:
        root_attr = getattr(args, "root", None)
        root = Path(root_attr).resolve() if root_attr else Config.ROOT_DIR
        return check(root)