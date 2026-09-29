import argparse
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.cve_gate import CveGateCommand  # noqa: E402


def _args(audit_path, allowlist_path):
    return argparse.Namespace(audit_path=str(audit_path), allowlist_path=str(allowlist_path))


def _audit(advisories):
    return {"advisories": advisories}


@pytest.fixture
def allowlist(tmp_path):
    path = tmp_path / "allow.json"
    path.write_text(json.dumps({"allowlisted_advisories": []}))
    return path


def _run(tmp_path, advisories, allowlist, name="audit.json"):
    audit_path = tmp_path / name
    audit_path.write_text(json.dumps(_audit(advisories)))
    return CveGateCommand().run(_args(audit_path, allowlist))


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
        allowlist.write_text(json.dumps({"allowlisted_advisories": [{"id": 4000}]}))
        advisories = {
            "4000": {"module_name": "allowed-pkg", "severity": "high"},
        }
        rc = _run(tmp_path, advisories, allowlist)
        assert rc == 0
        assert capsys.readouterr().out.strip() == "0"

    def test_allowlist_partially_suppresses(self, tmp_path, capsys):
        allowlist = tmp_path / "allow.json"
        allowlist.write_text(json.dumps({"allowlisted_advisories": [{"id": 4000}]}))
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
