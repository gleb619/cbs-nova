import argparse
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands import cve_gate  # noqa: E402
from cbs_cli.commands.cve_gate import CveGateCommand  # noqa: E402


@pytest.fixture(autouse=True)
def _reset_today_override():
    """T749: pin/reset the module-level 'today' seam so no test leaks state."""
    cve_gate._today_override = None
    yield
    cve_gate._today_override = None


def _args(audit_path, allowlist_path, today=None):
    return argparse.Namespace(
        audit_path=str(audit_path), allowlist_path=str(allowlist_path), today=today
    )


def _audit(advisories):
    return {"advisories": advisories}


@pytest.fixture
def allowlist(tmp_path):
    path = tmp_path / "allow.json"
    path.write_text(json.dumps({"allowlisted_advisories": []}))
    return path


def _run(tmp_path, advisories, allowlist, name="audit.json", today=None):
    audit_path = tmp_path / name
    audit_path.write_text(json.dumps(_audit(advisories)))
    return CveGateCommand().run(_args(audit_path, allowlist, today))


def _entry(**overrides):
    base = {"id": 4000, "package": "allowed-pkg", "expires_on": "2099-12-31"}
    base.update(overrides)
    return base


class TestCveGateRun:
    def test_no_advisories_prints_zero_and_returns_zero(self, tmp_path, allowlist, capsys):
        rc = _run(tmp_path, {}, allowlist)
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_high_severity_returns_one_and_prints_count(self, tmp_path, allowlist, capsys):
        advisories = {
            "1000": {"module_name": "lodash", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 1
        out = capsys.readouterr().out
        assert out.splitlines()[0] == "1"
        assert "high: lodash (advisory 1000)" in out

    def test_critical_severity_counts_and_is_listed(self, tmp_path, allowlist, capsys):
        advisories = {
            "2000": {"module_name": "minimist", "severity": "critical"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 1
        out = capsys.readouterr().out
        assert out.splitlines()[0] == "1"
        assert "critical: minimist (advisory 2000)" in out

    @pytest.mark.parametrize("severity", ["moderate", "low", "info"])
    def test_non_blocking_severities_ignored(self, tmp_path, allowlist, capsys, severity):
        advisories = {
            "3000": {"module_name": "pkg", "severity": severity},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_allowlisted_id_skipped(self, tmp_path, capsys):
        allowlist = tmp_path / "allow.json"
        allowlist.write_text(
            json.dumps({"allowlisted_advisories": [{"id": 4000, "package": "allowed-pkg", "expires_on": "2099-12-31"}]})
        )
        advisories = {
            "4000": {"module_name": "allowed-pkg", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_allowlist_partially_suppresses(self, tmp_path, capsys):
        allowlist = tmp_path / "allow.json"
        allowlist.write_text(
            json.dumps({"allowlisted_advisories": [{"id": 4000, "package": "allowed-pkg", "expires_on": "2099-12-31"}]})
        )
        advisories = {
            "4000": {"module_name": "allowed-pkg", "severity": "high"},
            "4001": {"module_name": "blocked-pkg", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 1
        out = capsys.readouterr().out
        assert out.splitlines()[0] == "1"
        assert "allowed-pkg" not in out
        assert "blocked-pkg" in out

    def test_missing_allowlist_file_tolerated(self, tmp_path, capsys):
        missing = tmp_path / "nope.json"
        advisories = {
            "5000": {"module_name": "lodash", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, missing)
        assert rc == 1
        assert "lodash" in capsys.readouterr().out

    def test_missing_allowlist_and_no_advisories_returns_zero(self, tmp_path):
        missing = tmp_path / "nope.json"
        assert _run(tmp_path, {}, missing) == 0

    def test_malformed_audit_json_returns_two(self, tmp_path, allowlist, capsys):
        audit_path = tmp_path / "audit.json"
        audit_path.write_text("{not json")
        rc = CveGateCommand().run(_args(audit_path, allowlist))
        assert rc == 2
        out = capsys.readouterr().out
        assert "[fail]" in out
        assert "could not parse" in out

    def test_malformed_allowlist_json_returns_two(self, tmp_path, capsys):
        allowlist = tmp_path / "allow.json"
        allowlist.write_text("[[[")
        advisories = {
            "6000": {"module_name": "lodash", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 2
        out = capsys.readouterr().out
        assert "could not parse" in out

    def test_more_than_twenty_advisories_truncates_listing(self, tmp_path, allowlist, capsys):
        advisories = {
            str(7000 + i): {"module_name": f"pkg-{i:02d}", "severity": "high"} for i in range(25)
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 1
        lines = capsys.readouterr().out.splitlines()
        assert lines[0] == "25"
        assert len(lines) == 21
        assert "pkg-00" in lines[1]
        assert "pkg-19" in lines[20]
        assert not any("pkg-20" in line for line in lines[1:])

    def test_missing_advisories_key_treated_as_empty(self, tmp_path, allowlist, capsys):
        audit_path = tmp_path / "audit.json"
        audit_path.write_text(json.dumps({"metadata": {}}))
        rc = CveGateCommand().run(_args(audit_path, allowlist))
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"


class TestCveGateExpiry:
    """T749 — the gate must honour the allowlist's mandatory `expires_on`."""

    def _allowlist_with(self, tmp_path, entries):
        path = tmp_path / "allow.json"
        path.write_text(json.dumps({"allowlisted_advisories": entries}))
        return path

    def test_future_expiry_still_allowlists(self, tmp_path, capsys):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on="2099-12-31")])
        advisories = {"4000": {"module_name": "allowed-pkg", "severity": "high"}}
        rc = _run(tmp_path, advisories, allowlist, today="2026-09-30")
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_past_expiry_no_longer_allowlists_and_is_named(self, tmp_path, capsys):
        allowlist = self._allowlist_with(
            tmp_path, [_entry(package="nuxt", expires_on="2026-01-31")]
        )
        advisories = {"4000": {"module_name": "nuxt", "severity": "high"}}
        rc = _run(tmp_path, advisories, allowlist, today="2026-09-30")
        assert rc == 1
        out = capsys.readouterr().out
        assert out.splitlines()[0] == "1"
        assert "expired allowlist entry 4000 (nuxt, expired 2026-01-31)" in out

    def test_expiry_on_today_is_still_valid_boundary(self, tmp_path, capsys):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on="2026-09-30")])
        advisories = {"4000": {"module_name": "allowed-pkg", "severity": "high"}}
        rc = _run(tmp_path, advisories, allowlist, today="2026-09-30")
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_expiry_day_after_boundary_is_expired(self, tmp_path, capsys):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on="2026-09-29")])
        advisories = {"4000": {"module_name": "allowed-pkg", "severity": "high"}}
        rc = _run(tmp_path, advisories, allowlist, today="2026-09-30")
        assert rc == 1
        assert "expired allowlist entry 4000" in capsys.readouterr().out

    def test_expired_entry_reported_even_when_advisory_absent(self, tmp_path, capsys):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on="2020-01-01")])
        rc = _run(tmp_path, {}, allowlist, today="2026-09-30")
        assert rc == 0
        out = capsys.readouterr().out
        assert out.splitlines()[0] == "0"
        assert "expired allowlist entry 4000 (allowed-pkg, expired 2020-01-01)" in out

    def test_missing_expires_on_returns_two(self, tmp_path, capsys):
        entry = _entry()
        del entry["expires_on"]
        allowlist = self._allowlist_with(tmp_path, [entry])
        rc = _run(tmp_path, {}, allowlist, today="2026-09-30")
        assert rc == 2
        out = capsys.readouterr().out
        assert "[fail]" in out
        assert "expires_on" in out
        assert "4000" in out

    @pytest.mark.parametrize("bad", ["31-12-2026", "2026-13-01", "not-a-date", "", 20261231])
    def test_unparseable_expires_on_returns_two(self, tmp_path, capsys, bad):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on=bad)])
        rc = _run(tmp_path, {}, allowlist, today="2026-09-30")
        assert rc == 2
        out = capsys.readouterr().out
        assert "[fail]" in out
        assert "expires_on" in out

    def test_null_expires_on_returns_two(self, tmp_path, capsys):
        allowlist = self._allowlist_with(tmp_path, [_entry(expires_on=None)])
        rc = _run(tmp_path, {}, allowlist, today="2026-09-30")
        assert rc == 2
        assert "expires_on" in capsys.readouterr().out

    def test_invalid_today_flag_returns_two(self, tmp_path, allowlist, capsys):
        rc = _run(tmp_path, {}, allowlist, today="not-a-date")
        assert rc == 2
        assert "--today" in capsys.readouterr().out

    def test_missing_allowlist_file_needs_no_expires_on(self, tmp_path, capsys):
        rc = _run(tmp_path, {}, tmp_path / "nope.json", today="2026-09-30")
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_repo_baseline_entries_all_have_valid_dates(self, tmp_path, capsys):
        """The committed baseline must stay valid or `make cve-scan` breaks."""
        repo_allowlist = (
            Path(__file__).resolve().parents[2] / "docs" / "security" / "pnpm-audit-baseline.json"
        )
        data = json.loads(repo_allowlist.read_text())
        entries = data["allowlisted_advisories"]
        assert entries, "baseline should not be empty"
        for entry in entries:
            assert entry.get("expires_on"), f"{entry.get('id')} has no expires_on"
            cve_gate._parse_date(entry["expires_on"])
