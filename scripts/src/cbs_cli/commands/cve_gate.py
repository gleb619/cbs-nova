import argparse
import json
from datetime import date
from pathlib import Path

from ..printer import Printer

# T749: module-level seam so tests can pin "today" without patching datetime.
_today_override: date | None = None


def today() -> date:
    """Current date, overridable for deterministic tests."""
    return _today_override or date.today()


def _parse_date(value: object) -> date:
    """Parse a strict ``YYYY-MM-DD`` string. Raises ValueError on anything else."""
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"expected a YYYY-MM-DD string, got {value!r}")
    return date.fromisoformat(value.strip())  # raises ValueError on bad format


class CveGateCommand:
    def run(self, args: argparse.Namespace) -> int:
        audit_path = Path(args.audit_path)
        allowlist_path = Path(args.allowlist_path)
        today_override = getattr(args, "today", None)
        global _today_override
        if today_override:
            try:
                _today_override = _parse_date(today_override)
            except ValueError as e:
                Printer.fail(f"invalid --today value {today_override!r}: {e}")
                return 2

        try:
            audit = json.loads(audit_path.read_text())
        except Exception as e:
            Printer.fail(f"could not parse {audit_path}: {e}")
            return 2

        allow: set[str] = set()
        expired: list[str] = []
        if allowlist_path.exists():
            try:
                d = json.loads(allowlist_path.read_text())
            except Exception as e:
                Printer.fail(f"could not parse {allowlist_path}: {e}")
                return 2

            reference = today()
            for entry in d.get("allowlisted_advisories", []) or []:
                entry_id = str(entry.get("id", "<missing-id>"))
                raw_expiry = entry.get("expires_on")
                if raw_expiry is None:
                    Printer.fail(
                        f"allowlist entry {entry_id} in {allowlist_path} is missing the "
                        "mandatory \"expires_on\" field (YYYY-MM-DD)"
                    )
                    return 2
                try:
                    expires_on = _parse_date(raw_expiry)
                except ValueError as e:
                    Printer.fail(
                        f"allowlist entry {entry_id} in {allowlist_path} has an invalid "
                        f"\"expires_on\": {e}"
                    )
                    return 2
                # Inclusive last day: an entry expires on expires_on + 1 day.
                if expires_on < reference:
                    package = str(entry.get("package") or "unknown-package")
                    expired.append(
                        f"  - expired allowlist entry {entry_id} "
                        f"({package}, expired {expires_on.isoformat()})"
                    )
                    continue
                allow.add(entry_id)

        advisories = audit.get("advisories", {}) or {}
        high = [
            (k, v.get("module_name"), v.get("severity"))
            for k, v in advisories.items()
            if v.get("severity") in ("high", "critical") and str(k) not in allow
        ]
        print(len(high))
        for k, m, s in high[:20]:
            print(f"  - {s}: {m} (advisory {k})")
        for line in expired:
            print(line)
        if not high:
            return 0
        return 1
