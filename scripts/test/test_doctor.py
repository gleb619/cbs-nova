import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.doctor import DoctorCommand  # noqa: E402
from cbs_cli.curl import Curl  # noqa: E402


class TestCheckBffSurface:
    def test_200_reports_ok(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "200")
        result = DoctorCommand._check_bff_surface("http://x/api/v1/executions", "Executions list")
        assert result == 0
        assert "[ok]" in capsys.readouterr().out

    def test_404_reports_warn(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "404")
        result = DoctorCommand._check_bff_surface("http://x/api/v1/dsl/schedules", "Schedules")
        assert result == 0
        out = capsys.readouterr().out
        assert "[warn]" in out
        assert "404" in out

    def test_connection_refused_reports_fail(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: None)
        result = DoctorCommand._check_bff_surface("http://x/api/v1/dsl/events", "Domain events")
        assert result == 1
        assert "[fail]" in capsys.readouterr().out

    def test_500_reports_fail(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "500")
        result = DoctorCommand._check_bff_surface("http://x/api/v1/dsl/audit", "Audit log")
        assert result == 1
        out = capsys.readouterr().out
        assert "[fail]" in out
        assert "500" in out


class TestCheckBffSurfaceGroup:
    def test_all_200_reports_ok(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "200")
        urls = ["http://x/api/v1/health", "http://x/api/v1/info"]
        result = DoctorCommand._check_bff_surface_group(urls, "Health / info")
        assert result == 0
        assert "[ok]" in capsys.readouterr().out

    def test_all_404_reports_warn(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "404")
        urls = ["http://x/api/v1/health", "http://x/api/v1/info"]
        result = DoctorCommand._check_bff_surface_group(urls, "Health / info")
        assert result == 0
        out = capsys.readouterr().out
        assert "[warn]" in out
        assert "404" in out

    def test_one_refused_one_200_reports_fail(self, monkeypatch, capsys):
        responses = {"http://x/api/v1/health": None, "http://x/api/v1/info": "200"}
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: responses[url])
        urls = list(responses.keys())
        result = DoctorCommand._check_bff_surface_group(urls, "Health / info")
        assert result == 1
        out = capsys.readouterr().out
        assert "[fail]" in out

    def test_mixed_404_and_200_reports_warn(self, monkeypatch, capsys):
        responses = {"http://x/api/v1/health": "404", "http://x/api/v1/info": "200"}
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: responses[url])
        urls = list(responses.keys())
        result = DoctorCommand._check_bff_surface_group(urls, "Health / info")
        assert result == 0
        out = capsys.readouterr().out
        assert "[warn]" in out
