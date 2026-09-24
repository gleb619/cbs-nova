import argparse

from ..config import Config
from ..frontend import Frontend
from ..gradle import Gradle
from ..printer import Printer

from .lint_kanban import LintKanbanCommand


class LintCommand:
    def run(self, args: argparse.Namespace) -> int:
        scope = args.scope
        if scope == "backend":
            return self._backend()
        if scope == "frontend":
            return self._frontend()
        if scope == "all":
            backend_failed = self._backend() != 0
            frontend_failed = self._frontend() != 0
            kanban_failed = LintKanbanCommand().run_check() != 0
            print("\n==> Lint summary:")
            if not backend_failed:
                Printer.ok(f"backend (spotlessCheck: {' '.join(Config.BACKEND_BUILDS)})")
            else:
                Printer.fail(f"backend (spotlessCheck: {' '.join(Config.BACKEND_BUILDS)})")
            if not frontend_failed:
                Printer.ok("frontend (biome lint)")
            else:
                Printer.fail("frontend (biome lint)")
            if not kanban_failed:
                Printer.ok("kanban (docs/kanban.md check mode)")
            else:
                Printer.fail("kanban (docs/kanban.md check mode)")
            if backend_failed or frontend_failed or kanban_failed:
                print("\nLint failed — run `make fmt` to auto-fix formatting.")
                return 1
            print("\nAll lint checks passed.")
            return 0
        return self._usage()

    @staticmethod
    def _backend() -> int:
        Printer.header(f"Running backend spotlessCheck ({' '.join(Config.BACKEND_BUILDS)})...")
        fails = 0
        for build in Config.BACKEND_BUILDS:
            print(f"  -> {build}")
            if Gradle.spotless_check(build):
                Printer.ok(f"{build} spotlessCheck")
            else:
                Printer.fail(f"{build} spotlessCheck")
                fails += 1
        if fails:
            print(f"\n{fails} backend build(s) failed spotlessCheck.")
            return 1
        print("\nBackend spotlessCheck passed.")

        Printer.header("Running lombok constructor check...")
        if Gradle.lombok_check():
            Printer.ok("lombok constructor check")
        else:
            Printer.fail("lombok constructor check")
            return 1

        return 0

    @staticmethod
    def _frontend() -> int:
        Printer.header("Running frontend lint (biome)...")
        if Frontend.lint() == 0:
            Printer.ok("frontend pnpm lint")
            return 0
        Printer.fail("frontend pnpm lint")
        return 1

    @staticmethod
    def _usage() -> int:
        print("usage: cbs_cli.py lint [all|backend|frontend]", file=sys.stderr)
        return 1
