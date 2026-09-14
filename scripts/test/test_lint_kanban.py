import subprocess
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.lint_kanban import (  # noqa: E402
    check,
    extract_ids,
    guard,
    parse_legend,
    parse_rows,
)

HEADER = "| ID   | Status  | Title       | Description | Priority | Owner | Blocks | Blocked By | Plan File |"
SEPARATOR = "|:-----|:--------|:------------|:------------|:---------|:------|:-------|:-----------|:----------|"
ROW_T1 = "| T1 | Backlog | First task  | Does things | Low      | loop  | -      | -          | - |"
ROW_T2 = (
    "| T2 | In Progress | Second task | Does more   | Low      | loop  | -      | -          "
    "| `./docs/plans/T2-plan.md` |"
)
ROW_T2_NO_PLAN = (
    "| T2 | In Progress | Second task | Does more   | Low      | loop  | -      | -          "
    "| - |"
)

KANBAN_TEMPLATE = """# Kanban Board

## Status Legend

| Status        | Meaning       | Who updates it  |
|---------------|---------------|-----------------|
| `Backlog`     | Not started.  | Planner / Agent |
| `In Progress` | Working on it.| Coding agent    |
| `Blocked`     | Stalled.      | Coding agent    |
| `Done`        | Completed.    | Coding agent    |

## Task Board

{header}
{separator}
{rows}
"""


def write_kanban(tmp_path: Path, rows: list[str]) -> Path:
    kanban = tmp_path / "docs" / "kanban.md"
    kanban.parent.mkdir(parents=True, exist_ok=True)
    kanban.write_text(
        KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows="\n".join(rows))
    )
    return kanban


class TestParser:
    def test_parse_legend(self):
        text = KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows=ROW_T1)
        assert parse_legend(text) == {"Backlog", "In Progress", "Blocked", "Done"}

    def test_parse_valid_rows(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, ROW_T2])
        rows = parse_rows(kanban.read_text())
        assert [r.task_id for r in rows] == ["T1", "T2"]
        assert rows[0].status == "Backlog"
        assert rows[1].status == "In Progress"
        assert rows[1].plan_file == "`./docs/plans/T2-plan.md`"
        assert all(r.ncols == 9 for r in rows)

    def test_ignores_non_task_rows(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, "| ID | whatever | not | a | task | row | x | y | z |"])
        rows = parse_rows(kanban.read_text())
        assert [r.task_id for r in rows] == ["T1"]

    def test_extract_ids(self):
        assert extract_ids(f"{ROW_T1}\n{ROW_T2}\nnot a row\n") == {"T1", "T2"}


class TestCheck:
    def test_valid_board_passes(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, ROW_T2_NO_PLAN])
        assert check(kanban, tmp_path) == 0

    def test_duplicate_id_fails(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, ROW_T1])
        assert check(kanban, tmp_path) == 1

    def test_invalid_status_fails(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, ROW_T2.replace("In Progress", "Doing")])
        assert check(kanban, tmp_path) == 1

    def test_missing_plan_file_fails(self, tmp_path):
        kanban = write_kanban(tmp_path, [ROW_T1, ROW_T2])
        assert check(kanban, tmp_path) == 1  # docs/plans/T2-plan.md not created yet
        (tmp_path / "docs" / "plans").mkdir(parents=True)
        (tmp_path / "docs" / "plans" / "T2-plan.md").write_text("# plan")
        assert check(kanban, tmp_path) == 0

    def test_column_count_mismatch_fails(self, tmp_path):
        short = "| T3 | Done | short | row |"
        kanban = write_kanban(tmp_path, [ROW_T1, short])
        assert check(kanban, tmp_path) == 1

    def test_escaped_pipe_in_cell_keeps_column_count(self, tmp_path):
        row = (
            "| T4 | Backlog | guard `(gray\\|blue)-` palette | desc | Low | loop | - | - | - |"
        )
        kanban = write_kanban(tmp_path, [ROW_T1, row])
        rows = parse_rows(kanban.read_text())
        assert rows[1].ncols == 9
        assert check(kanban, tmp_path) == 0


def git(repo: Path, *args: str) -> None:
    subprocess.run(["git", *args], cwd=repo, check=True, capture_output=True)


@pytest.fixture
def git_repo(tmp_path):
    repo = tmp_path / "repo"
    (repo / "docs").mkdir(parents=True)
    git(repo, "init", "-q")
    git(repo, "config", "user.email", "test@example.com")
    git(repo, "config", "user.name", "Test")
    (repo / "docs" / "kanban.md").write_text(
        KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows=f"{ROW_T1}\n{ROW_T2}")
    )
    git(repo, "add", ".")
    git(repo, "commit", "-q", "-m", "base: add kanban")
    return repo


class TestGuard:
    def test_no_rows_dropped_passes(self, git_repo):
        assert guard("HEAD", git_repo) == 0

    def test_dropped_row_fails(self, git_repo):
        (git_repo / "docs" / "kanban.md").write_text(
            KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows=ROW_T1)
        )
        git(git_repo, "add", ".")
        git(git_repo, "commit", "-q", "-m", "oops: silently drop T2")
        assert guard("HEAD~1", git_repo) == 1

    def test_rewrite_trailer_suppresses_failure(self, git_repo):
        (git_repo / "docs" / "kanban.md").write_text(
            KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows=ROW_T1)
        )
        git(git_repo, "add", ".")
        git(
            git_repo,
            "commit",
            "-q",
            "-m",
            "chore: prune stale rows\n\n[kanban-rewrite]",
        )
        assert guard("HEAD~1", git_repo) == 0

    def test_base_without_kanban_file_passes(self, tmp_path):
        # a ref that predates docs/kanban.md has no row IDs — nothing can be missing
        repo = tmp_path / "repo"
        repo.mkdir()
        git(repo, "init", "-q")
        git(repo, "config", "user.email", "test@example.com")
        git(repo, "config", "user.name", "Test")
        (repo / "README.md").write_text("# no kanban yet")
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "root: no kanban")
        (repo / "docs").mkdir()
        (repo / "docs" / "kanban.md").write_text(
            KANBAN_TEMPLATE.format(header=HEADER, separator=SEPARATOR, rows=ROW_T1)
        )
        git(repo, "add", ".")
        git(repo, "commit", "-q", "-m", "add kanban")
        assert guard("HEAD~1", repo) == 0
