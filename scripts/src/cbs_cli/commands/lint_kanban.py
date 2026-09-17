import argparse
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path

from ..config import Config
from ..printer import Printer

ROW_RE = re.compile(r"^\| T\d+ \|")
ID_RE = re.compile(r"^\| (T\d+) \|")
LEGEND_RE = re.compile(r"^\| `([^`]+)`")
# Markdown cells may contain escaped pipes (\|) — split on unescaped pipes only.
CELL_SPLIT_RE = re.compile(r"(?<!\\)\|")
REWRITE_TRAILER = "[kanban-rewrite]"


@dataclass
class KanbanRow:
    task_id: str
    status: str
    plan_file: str
    ncols: int
    line_no: int


def split_cells(line: str) -> list[str]:
    return [c.strip() for c in CELL_SPLIT_RE.split(line.strip().strip("|"))]


def parse_legend(text: str) -> set[str]:
    """Extract allowed Status values from the `## Status Legend` table."""
    statuses: set[str] = set()
    in_legend = False
    for line in text.splitlines():
        if line.startswith("## "):
            in_legend = line.strip() == "## Status Legend"
            continue
        if in_legend:
            m = LEGEND_RE.match(line)
            if m:
                statuses.add(m.group(1).strip())
    return statuses


def parse_rows(text: str) -> list[KanbanRow]:
    r"""Parse task-board rows matching `^\| T\d+ \|`."""
    rows = []
    for line_no, line in enumerate(text.splitlines(), start=1):
        if ROW_RE.match(line):
            cells = split_cells(line)
            rows.append(
                KanbanRow(
                    task_id=cells[0],
                    status=cells[1] if len(cells) > 1 else "",
                    plan_file=cells[-1] if len(cells) > 2 else "",
                    ncols=len(cells),
                    line_no=line_no,
                )
            )
    return rows


def header_ncols(text: str) -> int | None:
    for line in text.splitlines():
        if line.startswith("| ID"):
            return len(split_cells(line))
    return None


def extract_ids(text: str) -> set[str]:
    return {m.group(1) for m in (ID_RE.match(line) for line in text.splitlines()) if m}


def check(kanban_path: Path, root: Path) -> int:
    """Check mode: validate docs/kanban.md integrity. Returns 0 if clean."""
    text = kanban_path.read_text()
    legend = parse_legend(text)
    expected_ncols = header_ncols(text)
    rows = parse_rows(text)

    violations: list[str] = []
    seen: dict[str, int] = {}
    for row in rows:
        if expected_ncols is not None and row.ncols != expected_ncols:
            violations.append(
                f"line {row.line_no}: {row.task_id} has {row.ncols} columns, header has {expected_ncols}"
            )
        if row.task_id in seen:
            violations.append(
                f"line {row.line_no}: duplicate ID {row.task_id} (first seen at line {seen[row.task_id]})"
            )
        else:
            seen[row.task_id] = row.line_no
        if row.status not in legend:
            violations.append(
                f"line {row.line_no}: {row.task_id} has invalid Status '{row.status}' "
                f"(expected one of: {', '.join(sorted(legend))})"
            )
        plan = row.plan_file.strip("`").strip()
        if plan and plan != "-":
            # Tolerate missing plan file if any file for the same task ID exists in
            # docs/plans. This prevents false positives after `kanban-clean` removes
            # Done rows while their plan files remain.
            task_id = row.task_id
            plans_dir = root / "docs" / "plans"
            fallback_exists = False
            if plans_dir.exists() and task_id.startswith("T"):
                for candidate in plans_dir.iterdir():
                    if candidate.is_file() and candidate.name.startswith(task_id + "-"):
                        fallback_exists = True
                        break
            if not (root / plan).exists() and not fallback_exists:
                violations.append(
                    f"line {row.line_no}: {row.task_id} Plan File '{plan}' does not exist"
                )

    if violations:
        Printer.fail(f"docs/kanban.md: {len(violations)} violation(s) found:")
        for v in violations:
            print(f"  - {v}")
        return 1
    Printer.ok(f"docs/kanban.md ({len(rows)} rows: IDs unique, statuses valid, plan files resolve)")
    return 0


def _git(cwd: Path, *args: str) -> str:
    result = subprocess.run(
        ["git", *args], cwd=cwd, capture_output=True, text=True
    )
    return result.stdout if result.returncode == 0 else ""


def guard(base: str, cwd: Path) -> int:
    """Guard mode: fail if any row ID present at <base> is missing at HEAD,
    unless a commit in <base>..HEAD carries the [kanban-rewrite] trailer."""
    base_text = _git(cwd, "show", f"{base}:docs/kanban.md")
    head_path = cwd / "docs" / "kanban.md"
    head_text = head_path.read_text() if head_path.exists() else ""

    base_ids = extract_ids(base_text)
    head_ids = extract_ids(head_text)
    missing = sorted(base_ids - head_ids)

    if not missing:
        Printer.ok(f"kanban guard: no row IDs dropped since {base} ({len(base_ids)} IDs at base)")
        return 0

    log = _git(cwd, "log", f"{base}..HEAD", "--format=%B")
    if REWRITE_TRAILER in log:
        Printer.warn(
            f"kanban guard: {len(missing)} row ID(s) dropped since {base} "
            f"({', '.join(missing)}), but '{REWRITE_TRAILER}' trailer found — deliberate rewrite accepted"
        )
        return 0

    Printer.fail(f"kanban guard: {len(missing)} row ID(s) present at {base} but missing at HEAD:")
    for task_id in missing:
        print(f"  - {task_id}")
    print(
        f"If this pruning is deliberate, rebase with a commit message containing "
        f"the '{REWRITE_TRAILER}' trailer to acknowledge the rewrite."
    )
    return 1


class LintKanbanCommand:
    """Lint docs/kanban.md — check mode (default) or append-only guard mode (--base <ref>)."""

    def run(self, args: argparse.Namespace) -> int:
        if getattr(args, "base", None):
            return self.run_guard(args.base)
        return self.run_check()

    def run_check(self) -> int:
        Printer.header("Linting docs/kanban.md (check mode)...")
        return check(Config.ROOT_DIR / "docs" / "kanban.md", Config.ROOT_DIR)

    def run_guard(self, base: str) -> int:
        Printer.header(f"Guarding docs/kanban.md append-only since {base} (guard mode)...")
        return guard(base, Config.ROOT_DIR)
