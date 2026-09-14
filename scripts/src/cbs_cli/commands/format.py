import argparse

from ..config import Config
from ..frontend import Frontend
from ..gradle import Gradle
from ..printer import Printer


class FormatCommand:
    def run(self, args: argparse.Namespace) -> int:
        Printer.header("Applying backend formatting (spotlessApply)...")
        for build in Config.BACKEND_BUILDS:
            print(f"  -> {build}")
            if Gradle.spotless_apply(build):
                Printer.ok(f"{build} spotlessApply")
            else:
                Printer.warn(f"{build} spotlessApply failed (see output above)")

        Printer.header("Applying frontend formatting (biome format --write)...")
        if Frontend.format() == 0:
            Printer.ok("pnpm format")
        else:
            Printer.warn("pnpm format failed (see output above)")

        print("\nFormatting applied (exit 0 — re-run `make lint` to verify).")
        return 0
