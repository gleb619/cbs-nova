import argparse
import json
import re
import sys
from typing import List

from ..config import Config
from ..curl import Curl
from ..printer import Printer


class SeedHistoryCommand:
    def run(self, args: argparse.Namespace) -> int:
        Printer.header("Seeding historical sample runs (seed-history-*, cap 5)...")

        if Curl._base(Config.BFF_BASE_URL, timeout=5, fail=False).returncode != 0:
            Printer.fail(
                f"BFF not reachable at {Config.BFF_BASE_URL} — run: make up && make backend && make frontend (see: make doctor)"
            )
            return 1

        current = 0
        txs = Curl.get(f"{Config.BFF_BASE_URL}/api/v1/dsl/transactions", timeout=5, fail=False)
        if txs:
            try:
                data = json.loads(txs)
                current = sum(
                    1
                    for item in self._all_items(data)
                    if isinstance(item.get("name", ""), str) and item["name"].startswith("seed-history-")
                )
            except json.JSONDecodeError:
                current = len(re.findall(r"seed-history-", txs))

        if current >= 5:
            Printer.skip("already at cap (5) of seed-history-* runs")
            return 0

        remaining = 5 - current
        for i in range(remaining):
            n = 5 - remaining + i + 1
            payload = json.dumps({"body": {}, "metadata": {"tag": f"seed-history-{n}"}})
            if Curl.post(
                f"{Config.BFF_BASE_URL}/api/v1/dsl/run/seed-hello-world",
                payload,
                timeout=30,
            ):
                Printer.ok(f"seed-history-{n} triggered")
            else:
                Printer.warn(f"seed-history-{n} could not be triggered (best-effort, mixed statuses not guaranteed)")

        print(f"    Seeding history complete (up to {remaining} runs).")
        return 0

    @staticmethod
    def _all_items(data) -> List[dict]:
        if isinstance(data, dict):
            if "name" in data:
                return [data]
            items = []
            for v in data.values():
                items.extend(SeedHistoryCommand._all_items(v))
            return items
        if isinstance(data, list):
            items = []
            for item in data:
                items.extend(SeedHistoryCommand._all_items(item))
            return items
        return []
