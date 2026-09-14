import argparse
import json
import sys
from pathlib import Path

from ..printer import Printer


class CveGateCommand:
    def run(self, args: argparse.Namespace) -> int:
        audit_path = Path(args.audit_path)
        allowlist_path = Path(args.allowlist_path)

        try:
            audit = json.loads(audit_path.read_text())
        except Exception as e:
            Printer.fail(f"could not parse {audit_path}: {e}")
            return 2

        allow: set[str] = set()
        if allowlist_path.exists():
            try:
                d = json.loads(allowlist_path.read_text())
                allow = {str(a["id"]) for a in d.get("allowlisted_advisories", [])}
            except Exception as e:
                Printer.fail(f"could not parse {allowlist_path}: {e}")
                return 2

        advisories = audit.get("advisories", {}) or {}
        high = [
            (k, v.get("module_name"), v.get("severity"))
            for k, v in advisories.items()
            if v.get("severity") in ("high", "critical") and str(k) not in allow
        ]
        if not high:
            print(0)
            return 0
        print(len(high))
        for k, m, s in high[:20]:
            print(f"  - {s}: {m} (advisory {k})")
        return 1
