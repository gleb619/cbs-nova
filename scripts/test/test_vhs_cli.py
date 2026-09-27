import argparse
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.vhs import VhsCommand  # noqa: E402
from cbs_cli.config import Config  # noqa: E402
from cbs_cli.curl import Curl  # noqa: E402
from cbs_cli.runner import Runner  # noqa: E402


def _args(**kwargs):
    defaults = {
        "url": None,
        "vhs_action": None,
        "route": None,
        "since": None,
        "limit": 50,
        "identifier": None,
        "output": None,
        "target": "dry-run",
        "mode": "exact",
        "speed": 1.0,
        "concurrency": 1,
        "copies": 1,
        "tapes": "**/*.vhs.jsonl",
        "duration": 0,
    }
    defaults.update(kwargs)
    return argparse.Namespace(**defaults)


def _cmd(url="http://bff"):
    cmd = VhsCommand()
    cmd.base_url = url
    return cmd


TAPE_ITEM = {
    "filename": "20240101T000000_run-1_corr-1.vhs.jsonl",
    "recorded_at": "2024-01-01T00:00:00Z",
    "run_id": "run-1",
    "correlation_id": "corr-1",
    "route": "orders.create",
    "size_bytes": 128,
    "event_count": 4,
}


def _runner_result(code=0, stdout=""):
    return SimpleNamespace(returncode=code, stdout=stdout)


class TestResolveBaseUrl:
    def test_flag_wins(self, monkeypatch):
        monkeypatch.setenv("CBS_VHS_API_URL", "http://env")
        cmd = VhsCommand()
        assert cmd._resolve_base_url(_args(url="http://flag/")) == "http://flag"

    def test_env_var_second(self, monkeypatch):
        monkeypatch.setenv("CBS_VHS_API_URL", "http://env/")
        cmd = VhsCommand()
        assert cmd._resolve_base_url(_args()) == "http://env"

    def test_bff_base_url_default(self, monkeypatch):
        monkeypatch.delenv("CBS_VHS_API_URL", raising=False)
        monkeypatch.setattr(Config, "BFF_BASE_URL", "http://bff/")
        monkeypatch.setattr(Config, "BACKEND_BASE_URL", "http://backend")
        cmd = VhsCommand()
        assert cmd._resolve_base_url(_args()) == "http://bff"

    def test_backend_fallback(self, monkeypatch):
        monkeypatch.delenv("CBS_VHS_API_URL", raising=False)
        monkeypatch.setattr(Config, "BFF_BASE_URL", "")
        monkeypatch.setattr(Config, "BACKEND_BASE_URL", "http://backend/")
        cmd = VhsCommand()
        assert cmd._resolve_base_url(_args()) == "http://backend"


class TestFirstEnv:
    def test_picks_first_set(self, monkeypatch):
        monkeypatch.setenv("CBS_VHS_API_URL", "http://a")
        assert VhsCommand._first_env("CBS_VHS_API_URL", "NOPE_XYZ") == "http://a"

    def test_skips_empty(self, monkeypatch):
        monkeypatch.setenv("CBS_VHS_API_URL", "")
        monkeypatch.setenv("NOPE_XYZ", "http://b")
        assert VhsCommand._first_env("CBS_VHS_API_URL", "NOPE_XYZ") == "http://b"

    def test_none_when_missing(self, monkeypatch):
        monkeypatch.delenv("CBS_VHS_API_URL", raising=False)
        assert VhsCommand._first_env("CBS_VHS_API_URL") is None


class TestList:
    def test_happy_path(self, monkeypatch, capsys):
        body = json.dumps({"items": [TAPE_ITEM]})
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: body)
        result = _cmd()._list(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert "filename" in out and "run_id" in out
        assert "run-1" in out and "orders.create" in out

    def test_query_params(self, monkeypatch):
        seen = {}

        def fake_get(url, timeout, fail):
            seen["url"] = url
            return json.dumps({"items": []})

        monkeypatch.setattr(Curl, "get", fake_get)
        _cmd()._list(_args(route="a b", since="2024-01-01", limit=5))
        assert seen["url"] == (
            "http://bff/api/v1/vhs/tapes?route=a%20b&since=2024-01-01&limit=5"
        )

    def test_empty_list(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": []}))
        result = _cmd()._list(_args())
        assert result == 0
        assert "No tapes found." in capsys.readouterr().out

    def test_http_error(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: None)
        result = _cmd()._list(_args())
        assert result == 1
        assert "could not list tapes" in capsys.readouterr().out

    def test_non_json(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: "<html>")
        result = _cmd()._list(_args())
        assert result == 1
        assert "non-JSON" in capsys.readouterr().out

    def test_bare_list_body(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps([TAPE_ITEM]))
        result = _cmd()._list(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert "run-1" in out


class TestResolveRunId:
    def test_run_id_match(self, monkeypatch):
        body = json.dumps({"items": [TAPE_ITEM]})
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: body)
        assert _cmd()._resolve_run_id("run-1") == "run-1"

    def test_correlation_id_resolves_to_run_id(self, monkeypatch):
        body = json.dumps({"items": [TAPE_ITEM]})
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: body)
        assert _cmd()._resolve_run_id("corr-1") == "run-1"

    def test_filename_match(self, monkeypatch):
        body = json.dumps({"items": [TAPE_ITEM]})
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: body)
        assert _cmd()._resolve_run_id("20240101T000000_run-1_corr-1.vhs.jsonl") == "run-1"

    def test_unknown_identifier_falls_back(self, monkeypatch):
        body = json.dumps({"items": [TAPE_ITEM]})
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: body)
        assert _cmd()._resolve_run_id("unknown-id") == "unknown-id"

    def test_no_body_returns_identifier(self, monkeypatch):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: None)
        assert _cmd()._resolve_run_id("run-9") == "run-9"

    def test_non_json_body_returns_identifier(self, monkeypatch):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: "oops")
        assert _cmd()._resolve_run_id("run-9") == "run-9"

    def test_path_like_identifier_uses_filename(self, monkeypatch):
        # no HTTP: "/" short-circuits to filename parsing
        def boom(url, timeout, fail):
            raise AssertionError("no HTTP expected")

        monkeypatch.setattr(Curl, "get", boom)
        assert _cmd()._resolve_run_id("tapes/20240101T000000_run-2_corr-2.vhs.jsonl") == "run-2"

    def test_filename_without_underscores(self, monkeypatch):
        def boom(url, timeout, fail):
            raise AssertionError("no HTTP expected")

        monkeypatch.setattr(Curl, "get", boom)
        assert _cmd()._resolve_run_id("nounder.vhs.jsonl") is None


class TestRunIdFromFilename:
    def test_full_name(self):
        assert VhsCommand._run_id_from_filename("20240101T000000_run-1_corr-1.vhs.jsonl") == "run-1"

    def test_two_parts(self):
        assert VhsCommand._run_id_from_filename("ts_run-1.vhs.jsonl") == "run-1"

    def test_bad_suffix(self):
        assert VhsCommand._run_id_from_filename("run-1.json") is None


class TestDownload:
    def test_stdout_download(self, monkeypatch, capsys):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0)

        monkeypatch.setattr(Runner, "run", fake_run)
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._download(_args(identifier="run-1"))
        assert result == 0
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/tapes/run-1"
        assert "-o" not in seen["cmd"]

    def test_output_file(self, monkeypatch):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0)

        monkeypatch.setattr(Runner, "run", fake_run)
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._download(_args(identifier="run-1", output="/tmp/x.jsonl"))
        assert result == 0
        assert seen["cmd"][-3:-1] == ["-o", "/tmp/x.jsonl"]
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/tapes/run-1"

    def test_missing_identifier(self, monkeypatch, capsys):
        def boom(url, timeout, fail):
            return None

        monkeypatch.setattr(Curl, "get", boom)
        # body None → fallback returns identifier → download proceeds; force None via filename parse
        result = _cmd()._download(_args(identifier="bad.vhs.jsonl"))
        assert result == 1
        assert "no tape matches identifier" in capsys.readouterr().out

    def test_curl_failure(self, monkeypatch, capsys):
        # stdout-download path is silent on failure; only exit code signals it
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(22))
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._download(_args(identifier="run-1"))
        assert result == 1
        assert capsys.readouterr().out == ""

    def test_curl_failure_with_output(self, monkeypatch, capsys):
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(22))
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._download(_args(identifier="run-1", output="/tmp/x.jsonl"))
        assert result == 1
        assert "download failed" in capsys.readouterr().out

    def test_identifier_escaped_in_path(self, monkeypatch):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0)

        monkeypatch.setattr(Runner, "run", fake_run)
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: None)
        # "/" forces filename parsing (basename); run id with space exercises path escaping
        result = _cmd()._download(_args(identifier="tapes/20240101T000000_run id_corr.vhs.jsonl"))
        assert result == 0
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/tapes/run%20id"


class TestDelete:
    def test_happy_path(self, monkeypatch, capsys):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0)

        monkeypatch.setattr(Runner, "run", fake_run)
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._delete(_args(identifier="run-1"))
        assert result == 0
        assert seen["cmd"][:2] == ["curl", "--silent"]
        assert "-X" in seen["cmd"] and "DELETE" in seen["cmd"]
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/tapes/run-1"
        assert "deleted run-1" in capsys.readouterr().out

    def test_failure(self, monkeypatch, capsys):
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(1))
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._delete(_args(identifier="run-1"))
        assert result == 1
        assert "delete failed" in capsys.readouterr().out

    def test_missing_identifier(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: None)
        result = _cmd()._delete(_args(identifier="bad.vhs.jsonl"))
        assert result == 1
        assert "no tape matches identifier" in capsys.readouterr().out


class TestReplay:
    def test_happy_path(self, monkeypatch, capsys):
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0, '{"replayed": true}')

        monkeypatch.setattr(Runner, "run", fake_run)
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        args = _args(identifier="run-1", target="local", mode="load", speed=2.0, concurrency=3, copies=4)
        result = _cmd()._replay(args)
        out = capsys.readouterr().out
        assert result == 0
        assert '{"replayed": true}' in out
        body = json.loads(seen["cmd"][seen["cmd"].index("-d") + 1])
        assert body == {"target": "local", "mode": "load", "speed": 2.0, "concurrency": 3, "copies": 4}
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/tapes/run-1/replay"

    def test_failure_prints_stdout(self, monkeypatch, capsys):
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(1, "server error"))
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: json.dumps({"items": [TAPE_ITEM]}))
        result = _cmd()._replay(_args(identifier="run-1"))
        out = capsys.readouterr().out
        assert result == 1
        assert "replay failed" in out
        assert "server error" in out

    def test_missing_identifier(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: None)
        result = _cmd()._replay(_args(identifier="bad.vhs.jsonl"))
        assert result == 1
        assert "no tape matches identifier" in capsys.readouterr().out


class TestLoadtest:
    def test_production_guard_blocks(self, monkeypatch, capsys):
        monkeypatch.delenv("CBS_VHS_REPLAY_ALLOW_PRODUCTION", raising=False)
        result = _cmd()._loadtest(_args(target="https://prod.example"))
        assert result == 1
        assert "Refusing load-test" in capsys.readouterr().out

    def test_production_guard_env_only_insufficient(self, monkeypatch, capsys):
        monkeypatch.setenv("CBS_VHS_REPLAY_ALLOW_PRODUCTION", "1")
        result = _cmd()._loadtest(_args(target="prod"))
        assert result == 1

    def test_happy_path(self, monkeypatch, capsys):
        report = {
            "target": "dry-run",
            "tapesLoaded": 1,
            "totalCalls": 10,
            "successfulCalls": 9,
            "failedCalls": 1,
            "wallClockMs": 1234,
            "tapeSummaries": [
                {
                    "tapeName": "tape-a",
                    "totalCalls": 10,
                    "successfulCalls": 9,
                    "failedCalls": 1,
                    "p50Ms": 5,
                    "p95Ms": 9,
                    "p99Ms": 12,
                    "errorRatePct": 10.0,
                }
            ],
        }
        seen = {}

        def fake_run(cmd, **kwargs):
            seen["cmd"] = cmd
            return _runner_result(0, json.dumps(report))

        monkeypatch.setattr(Runner, "run", fake_run)
        result = _cmd()._loadtest(_args(duration=30, concurrency=4))
        out = capsys.readouterr().out
        assert result == 0
        assert "target: dry-run  tapes: 1  calls: 10  success: 9  failed: 1  wall: 1234ms" in out
        assert "tape-a" in out
        assert "10.0%" in out
        body = json.loads(seen["cmd"][seen["cmd"].index("-d") + 1])
        assert body["duration"] == 30000
        assert seen["cmd"][-1] == "http://bff/api/v1/vhs/loadtest"

    def test_non_json_report_passthrough(self, monkeypatch, capsys):
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(0, "raw text"))
        result = _cmd()._loadtest(_args())
        assert result == 0
        assert "raw text" in capsys.readouterr().out

    def test_request_failure(self, monkeypatch, capsys):
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: _runner_result(1, "boom"))
        result = _cmd()._loadtest(_args())
        out = capsys.readouterr().out
        assert result == 1
        assert "load-test request failed" in out
        assert "boom" in out


class TestPrintLoadtestReport:
    def test_empty_summaries(self, capsys):
        VhsCommand._print_loadtest_report({"target": "dry-run", "tapeSummaries": []})
        out = capsys.readouterr().out
        assert "target: dry-run" in out
        assert "No per-tape summaries." in out

    def test_defaults(self, capsys):
        VhsCommand._print_loadtest_report({})
        out = capsys.readouterr().out
        assert "target: ?  tapes: 0  calls: 0  success: 0  failed: 0  wall: 0ms" in out
        assert "No per-tape summaries." in out


class TestRunDispatch:
    def test_unknown_subcommand(self, capsys):
        cmd = VhsCommand()
        result = cmd.run(_args(vhs_action="bogus"))
        assert result == 1
        assert "unknown vhs subcommand" in capsys.readouterr().out
