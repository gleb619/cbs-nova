import argparse
import sys

from .commands.cve_gate import CveGateCommand
from .commands.dev import DevCommand
from .commands.doctor import DoctorCommand
from .commands.format import FormatCommand
from .commands.lint import LintCommand
from .commands.loadtest import LoadtestCommand
from .commands.openapi_diff import OpenApiDiffCommand
from .commands.openapi_fetch import OpenApiFetchCommand
from .commands.seed import SeedCommand
from .commands.seed_history import SeedHistoryCommand
from .commands.services import ServicesCommand
from .commands.stack import StackCommand


class CLI:
    def __init__(self) -> None:
        self.parser = argparse.ArgumentParser(
            prog="cbs-cli", description="Orchestrate the cbs-nova local development stack."
        )
        subparsers = self.parser.add_subparsers(dest="command")

        # Stack actions
        for name, help_text, defaults in [
            ("up", "Start the docker compose stack", {"handler": StackCommand, "action": "up"}),
            ("down", "Stop the docker compose stack", {"handler": StackCommand, "action": "down"}),
            ("logs", "Tail docker compose logs", {"handler": StackCommand, "action": "logs"}),
            ("clean", "Stop stack and delete volumes", {"handler": StackCommand, "action": "clean"}),
        ]:
            p = subparsers.add_parser(name, help=help_text)
            p.set_defaults(**defaults)

        # Services
        for name, help_text, defaults in [
            ("backend", "Run the Spring Boot starter", {"handler": ServicesCommand, "service": "backend"}),
            ("frontend", "Run the Nuxt admin UI dev server", {"handler": ServicesCommand, "service": "frontend"}),
            ("publish", "Publish the DSL platform to Maven Local", {"handler": ServicesCommand, "service": "publish"}),
        ]:
            p = subparsers.add_parser(name, help=help_text)
            p.set_defaults(**defaults)

        # Dev
        subparsers.add_parser("dev", help="Run backend and frontend together").set_defaults(handler=DevCommand)

        # Lint
        p = subparsers.add_parser("lint", help="Run all lint/format checks")
        p.add_argument("scope", nargs="?", choices=["all", "backend", "frontend"], default="all")
        p.set_defaults(handler=LintCommand)

        subparsers.add_parser("lint-backend", help="Run backend spotlessCheck").set_defaults(
            handler=LintCommand, scope="backend"
        )
        subparsers.add_parser("lint-frontend", help="Run frontend Biome lint").set_defaults(
            handler=LintCommand, scope="frontend"
        )

        # Format
        subparsers.add_parser("fmt", help="Apply formatting everywhere").set_defaults(handler=FormatCommand)

        # Doctor / seed / loadtest
        subparsers.add_parser("doctor", help="Run smoke checks").set_defaults(handler=DoctorCommand)
        subparsers.add_parser("seed", help="Seed a hello-world DSL definition").set_defaults(handler=SeedCommand)
        subparsers.add_parser("seed-history", help="Seed historical sample runs").set_defaults(
            handler=SeedHistoryCommand
        )
        subparsers.add_parser("loadtest", help="Load-test read-only BFF endpoints").set_defaults(
            handler=LoadtestCommand
        )

        # Security / OpenAPI
        p = subparsers.add_parser("cve-scan", help="Check pnpm audit against an allowlist")
        p.add_argument("audit_path", help="pnpm audit --json output file")
        p.add_argument("allowlist_path", help="baseline allowlist JSON")
        p.set_defaults(handler=CveGateCommand)

        p = subparsers.add_parser("openapi-diff", help="Classify OpenAPI additive vs breaking changes")
        p.add_argument("old_path", help="older OpenAPI JSON spec")
        p.add_argument("new_path", help="newer OpenAPI JSON spec")
        p.set_defaults(handler=OpenApiDiffCommand)

        p = subparsers.add_parser("openapi-fetch", help="Boot backend and fetch normalized OpenAPI spec")
        p.add_argument("outpath", help="output JSON path")
        p.set_defaults(handler=OpenApiFetchCommand)

    def run(self) -> int:
        args = self.parser.parse_args()
        if not hasattr(args, "handler"):
            self.parser.print_help()
            return 1
        return args.handler().run(args)
