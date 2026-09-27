import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.worktree_seed import (
    WorktreeSeedCommand,
    _derive_suffix,
    _is_linked_worktree,
    _sanitize_suffix,
    build_dirs,
    seed,
    write_worktree_version,
)


def _touch(p: Path, text: str = "x") -> None:
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(text)


def _fixture(root: Path) -> None:
    _touch(root / "backend/dsl-platform/gradle/wrapper/gradle-wrapper.jar", "jar")
    _touch(root / "backend/dsl-platform/dsl/build/classes/A.class")
    _touch(root / "backend/dsl-starter/build/x")
    _touch(root / "frontend/node_modules/pkg/index.js")
    (root / "frontend/node_modules/link").symlink_to("pkg")
    _touch(root / "unrelated/build/y")


def test_build_dirs_finds_module_builds(tmp_path):
    _fixture(tmp_path)
    assert sorted(build_dirs(tmp_path)) == ["backend/dsl-platform/dsl/build", "backend/dsl-starter/build"]


def test_seed_copies_and_keeps_symlinks(tmp_path):
    src, dst = tmp_path / "src", tmp_path / "dst"
    _fixture(src)
    dst.mkdir()
    copied = seed(src, dst)
    assert "backend/dsl-platform/gradle/wrapper/gradle-wrapper.jar" in copied
    assert (dst / "backend/dsl-platform/dsl/build/classes/A.class").exists()
    assert (dst / "frontend/node_modules/link").is_symlink()
    assert not (dst / "unrelated").exists()


def test_seed_skips_existing_and_missing(tmp_path):
    src, dst = tmp_path / "src", tmp_path / "dst"
    _fixture(src)
    _touch(dst / "frontend/node_modules/keep", "mine")
    copied = seed(src, dst)
    assert "frontend/node_modules" not in copied
    assert not (dst / "frontend/node_modules/pkg").exists()
    assert seed(src, dst) == []


def test_extra_path(tmp_path):
    src, dst = tmp_path / "src", tmp_path / "dst"
    _touch(src / "foo/bar.txt")
    dst.mkdir()
    assert seed(src, dst, ["foo"]) == ["foo"]


def test_command_rejects_missing_dest(tmp_path):
    class A:
        dest = str(tmp_path / "nope")
        src = None
        extra: list = []
        version_suffix = None

    assert WorktreeSeedCommand().run(A()) == 1


def test_is_linked_worktree_detects_git_file(tmp_path):
    worktree = tmp_path / "wt"
    worktree.mkdir()
    (worktree / ".git").write_text("gitdir: /path/to/main/.git/worktrees/wt\n")
    assert _is_linked_worktree(worktree) is True


def test_is_linked_worktree_skips_regular_directory(tmp_path):
    regular = tmp_path / "regular"
    regular.mkdir()
    (regular / ".git").mkdir()
    assert _is_linked_worktree(regular) is False


def test_sanitize_suffix_keeps_safe_characters():
    assert _sanitize_suffix("feat/T123_4.x-") == "featT123_4.x-"


def test_sanitize_suffix_falls_back_when_empty():
    assert _sanitize_suffix("!!!") == "worktree"


def test_derive_suffix_uses_branch_basename(monkeypatch, tmp_path):
    captured = []
    original_run = __import__("subprocess").run

    def fake_run(cmd, **kwargs):
        captured.append(cmd)
        class R:
            returncode = 0
            stdout = "feat/T123\n"
            stderr = ""
        return R()

    monkeypatch.setattr("subprocess.run", fake_run)
    suffix = _derive_suffix(tmp_path)
    assert suffix == "T123"
    assert captured[0][:4] == ["git", "-C", str(tmp_path), "rev-parse"]


def test_derive_suffix_falls_back_to_dir_name():
    dest = Path("/tmp/cbs-nova-T456")
    assert _derive_suffix(dest) == "cbs-nova-T456"


def test_write_worktree_version_creates_marker_for_linked_worktree(tmp_path):
    worktree = tmp_path / "cbs-nova-T123"
    worktree.mkdir()
    (worktree / ".git").write_text("gitdir: /main/.git/worktrees/T123\n")
    marker = write_worktree_version(worktree)
    assert marker == worktree / ".worktree-version"
    assert marker.read_text().strip() == "cbs-nova-T123"


def test_write_worktree_version_uses_override_suffix(tmp_path):
    worktree = tmp_path / "cbs-nova-task"
    worktree.mkdir()
    (worktree / ".git").write_text("gitdir: /main/.git/worktrees/task\n")
    marker = write_worktree_version(worktree, "custom-suffix")
    assert marker.read_text().strip() == "custom-suffix"


def test_write_worktree_version_skips_non_worktree(tmp_path):
    regular = tmp_path / "main"
    regular.mkdir()
    (regular / ".git").mkdir()
    assert write_worktree_version(regular) is None


def test_command_writes_marker(monkeypatch, tmp_path):
    src, dst = tmp_path / "src", tmp_path / "dst"
    src.mkdir()
    dst.mkdir()
    (dst / ".git").write_text("gitdir: /main/.git/worktrees/dst\n")

    args = type("Args", (), {
        "dest": str(dst),
        "src": str(src),
        "extra": [],
        "version_suffix": "T789",
    })()

    monkeypatch.setattr("cbs_cli.commands.worktree_seed.seed", lambda s, d, e: [])
    assert WorktreeSeedCommand().run(args) == 0
    assert (dst / ".worktree-version").read_text().strip() == "T789"
