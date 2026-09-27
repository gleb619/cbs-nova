import argparse
import shutil
import subprocess
from pathlib import Path

from ..config import Config
from ..printer import Printer

# Git-ignored paths (relative to repo root) worth carrying into a fresh worktree so it builds without re-download.
SEED_PATHS = [
    "gradle/wrapper/gradle-wrapper.jar",
    "backend/dsl-platform/gradle/wrapper/gradle-wrapper.jar",
    "backend/dsl-plugins/gradle/wrapper/gradle-wrapper.jar",
    "backend/dsl-starter/gradle/wrapper/gradle-wrapper.jar",
    "app/dsl/gradle/wrapper/gradle-wrapper.jar",
    "app/server/gradle/wrapper/gradle-wrapper.jar",
    "frontend/node_modules",
    "frontend/admin-ui-plugin/node_modules",
    "frontend/components/node_modules",
    "frontend/admin-ui-plugin/.nuxt",
    "scripts/kanban/node_modules",
    "docs/plans",
]
# Build output dirs: every module `build/` under these roots (incremental compile cache).
BUILD_ROOTS = ["backend/dsl-platform", "backend/dsl-plugins", "backend/dsl-starter"]


def build_dirs(src: Path) -> list[str]:
    found = []
    for root in BUILD_ROOTS:
        base = src / root
        if not base.is_dir():
            continue
        candidates = [base / "build", *(p / "build" for p in base.iterdir() if p.is_dir() and p.name != "build")]
        found += [str(c.relative_to(src)) for c in candidates if c.is_dir()]
    return found


def copy_path(src: Path, dst: Path) -> None:
    dst.parent.mkdir(parents=True, exist_ok=True)
    if shutil.which("cp"):
        # -a keeps symlinks (pnpm) + mtimes; reflink makes it near-free on btrfs/xfs.
        r = subprocess.run(["cp", "-a", "--reflink=auto", str(src), str(dst)], capture_output=True)
        if r.returncode == 0:
            return
    if src.is_dir():
        shutil.copytree(src, dst, symlinks=True, dirs_exist_ok=True)
    else:
        shutil.copy2(src, dst)


def seed(src_root: Path, dest_root: Path, extra: list[str] | None = None) -> list[str]:
    """Copy ignored-but-useful paths from src_root into dest_root; skip missing sources and existing targets."""
    copied = []
    for rel in [*SEED_PATHS, *build_dirs(src_root), *(extra or [])]:
        src, dst = src_root / rel, dest_root / rel
        if not src.exists() or dst.exists():
            continue
        copy_path(src, dst)
        copied.append(rel)
    return copied


class WorktreeSeedCommand:
    """Seed a new git worktree with git-ignored build prerequisites (gradle wrapper, node_modules, build/)."""

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        parser.add_argument("dest", help="worktree directory to seed")
        parser.add_argument("--src", default=None, help="source checkout (default: repo root)")
        parser.add_argument("--extra", action="append", default=[], help="extra relative path to copy (repeatable)")

    def run(self, args: argparse.Namespace) -> int:
        dest = Path(args.dest).resolve()
        src = Path(args.src).resolve() if args.src else Config.ROOT_DIR
        if not dest.is_dir():
            Printer.fail(f"worktree not found: {dest}")
            return 1
        if dest == src:
            Printer.fail("dest equals src")
            return 1
        Printer.header(f"Seeding {dest} from {src}...")
        copied = seed(src, dest, args.extra)
        for rel in copied:
            Printer.ok(rel)
        if not copied:
            Printer.skip("nothing to copy")
        return 0
