import argparse
import json
import sys

from ..config import Config
from ..curl import Curl
from ..printer import Printer


class SeedCommand:
    DRAFT_BODY = json.dumps({
        "name": "seed-hello-world",
        "type": "Process",
        "status": "Draft",
        "version": "1",
        "taskQueue": "default",
        "description": "seeded by make seed (uuidV7 + formatMessage catalog helpers)",
    })
    PUB_BODY = json.dumps({
        "name": "seed-hello-world",
        "type": "Process",
        "status": "Published",
        "version": "1",
        "taskQueue": "default",
        "description": "seeded by make seed (uuidV7 + formatMessage catalog helpers)",
    })

    def run(self, args: argparse.Namespace) -> int:
        Printer.header("Seeding sample DSL definition (seed-hello-world)...")

        if Curl._base(Config.BFF_BASE_URL, timeout=5, fail=False).returncode != 0:
            Printer.fail(
                f"BFF not reachable at {Config.BFF_BASE_URL} — run: make up && make backend && make frontend (see: make doctor)"
            )
            return 1

        defs = Curl.get(f"{Config.BFF_BASE_URL}/api/v1/dsl/definitions", timeout=5, fail=False)
        if self._definition_exists(defs, "seed-hello-world"):
            Printer.skip("seed-hello-world already present")
            return 0

        if not Curl.post(
            f"{Config.BFF_BASE_URL}/api/v1/dsl/drafts/seed-hello-world/save",
            self.DRAFT_BODY,
            timeout=10,
        ):
            Printer.fail("save draft seed-hello-world")
            return 1
        Printer.ok("saved draft seed-hello-world")

        if not Curl.post(
            f"{Config.BFF_BASE_URL}/api/v1/dsl/drafts/seed-hello-world/publish",
            self.PUB_BODY,
            timeout=15,
        ):
            Printer.fail("publish seed-hello-world")
            return 1
        Printer.ok("published seed-hello-world")

        Printer.ok("runnable definition seed-hello-world ready (uses uuidV7 + formatMessage helpers)")

        if Curl.post(
            f"{Config.BFF_BASE_URL}/api/v1/dsl/run/seed-hello-world",
            json.dumps({"body": {}, "metadata": {"source": "make seed"}}),
            timeout=30,
        ):
            Printer.ok("triggered sample run for seed-hello-world")
        else:
            Printer.warn("could not trigger sample run for seed-hello-world (run it from the dashboard)")
        return 0

    @staticmethod
    def _definition_exists(body: str, name: str) -> bool:
        if not body:
            return False
        try:
            data = json.loads(body)
            return SeedCommand._find_name(data, name)
        except json.JSONDecodeError:
            return f'"name":"{name}"' in body

    @staticmethod
    def _find_name(data, name: str) -> bool:
        if isinstance(data, dict):
            if data.get("name") == name:
                return True
            return any(SeedCommand._find_name(v, name) for v in data.values())
        if isinstance(data, list):
            return any(SeedCommand._find_name(item, name) for item in data)
        return False
