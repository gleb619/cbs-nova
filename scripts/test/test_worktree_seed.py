import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.worktree_seed import WorktreeSeedCommand, build_dirs, seed  # noqa: E402


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

    assert WorktreeSeedCommand().run(A()) == 1
