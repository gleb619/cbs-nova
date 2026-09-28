import argparse
import io
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.openapi_fetch import OpenApiFetchCommand  # noqa: E402
from cbs_cli.runner import Runner  # noqa: E402


class _FakeProc:
    def __init__(self):
        self.terminated = False
        self.killed = False
        self.waited_with_timeout = None
        self._returncode = None

    def poll(self):
        return self._returncode

    def terminate(self):
        self.terminated = True
        self._returncode = 0

    def wait(self, timeout=None):
        self.waited_with_timeout = timeout
        return self._returncode if self._returncode is not None else 0

    def kill(self):
        self.killed = True
        self._returncode = -9


class _Result:
    def __init__(self, returncode=0, stdout=""):
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = ""


def _up():
    return _Result(returncode=0, stdout='{"status":"UP"}')


def _down():
    return _Result(returncode=22, stdout="")


def _ok(body):
    return _Result(returncode=0, stdout=body)


def _make_run(*results):
    iter_results = iter(results)
    calls = []

    def fake(cmd, *args, **kwargs):
        calls.append((cmd, args, kwargs))
        try:
            return next(iter_results)
        except StopIteration:
            return _Result()

    fake.calls = calls
    return fake


@pytest.fixture
def proc():
    return _FakeProc()


@pytest.fixture
def outpath(tmp_path):
    return tmp_path / "openapi.json"


@pytest.fixture
def args(outpath):
    return argparse.Namespace(outpath=str(outpath))


@pytest.fixture
def patch_subprocess(monkeypatch, proc):
    monkeypatch.setattr(
        "cbs_cli.commands.openapi_fetch.subprocess.Popen",
        lambda *a, **kw: proc,
    )


@pytest.fixture
def fast_sleep(monkeypatch):
    monkeypatch.setattr(
        "cbs_cli.commands.openapi_fetch.time.sleep",
        lambda *a, **kw: None,
    )


class TestEnvValidation:
    def test_missing_min_openapi_paths_returns_2(self, monkeypatch, capsys, args):
        monkeypatch.delenv("MIN_OPENAPI_PATHS", raising=False)
        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()
        assert result == 2
        assert "MIN_OPENAPI_PATHS env var is required" in captured.out

    def test_invalid_min_openapi_paths_returns_2(self, monkeypatch, capsys, args):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "not-a-number")
        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()
        assert result == 2
        assert "MIN_OPENAPI_PATHS invalid: not-a-number" in captured.out


class TestHappyPath:
    def test_success_writes_outpath(
        self, monkeypatch, args, outpath, proc, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "3")
        monkeypatch.setenv("SERVER_PORT", "12345")

        body = {"openapi": "3.0.0", "paths": {f"/p{i}": {} for i in range(3)}}
        run = _make_run(_up(), _ok(json.dumps(body)), _Result(), _down(), _Result())
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)

        assert result == 0
        assert outpath.exists()
        assert json.loads(outpath.read_text()) == body
        assert proc.terminated is True

    def test_health_and_api_docs_urls_and_timeouts_pinned(
        self, monkeypatch, args, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "1")
        monkeypatch.setenv("SERVER_PORT", "12345")

        body = {"paths": {"/a": {}}}
        run = _make_run(_up(), _ok(json.dumps(body)), _Result(), _down(), _Result())
        monkeypatch.setattr(Runner, "run", run)

        OpenApiFetchCommand().run(args)

        assert run.calls[0][0] == [
            "curl",
            "-sf",
            "--max-time",
            "2",
            "http://localhost:12345/actuator/health",
        ]

        assert run.calls[1][0] == [
            "curl",
            "-sf",
            "--max-time",
            "30",
            "http://localhost:12345/v3/api-docs",
        ]

        assert run.calls[3][0] == [
            "curl",
            "-s",
            "--max-time",
            "1",
            "http://localhost:12345",
        ]

    def test_output_is_sorted_indented_with_trailing_newline(
        self, monkeypatch, args, outpath, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "1")
        monkeypatch.delenv("SERVER_PORT", raising=False)

        body = {"b": 1, "a": 2, "paths": {"/z": {}}}
        run = _make_run(_up(), _ok(json.dumps(body)), _Result(), _down(), _Result())
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)
        text = outpath.read_text()

        assert result == 0
        assert text.index('"a"') < text.index('"b"')
        assert "\n  " in text
        assert text.endswith("\n")


class TestHealthTimeout:
    def test_health_never_up_returns_1_after_40_probes(
        self, monkeypatch, capsys, args, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "3")
        monkeypatch.setenv("SERVER_PORT", "12345")

        run = _make_run(*([_down()] * 40))
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()

        assert result == 1
        assert "backend not healthy after ~120s" in (captured.out + captured.err)
        assert len(run.calls) == 41


class TestApiDocsFailure:
    def test_api_docs_curl_failure_returns_1(
        self, monkeypatch, capsys, args, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "3")
        monkeypatch.setenv("SERVER_PORT", "12345")

        run = _make_run(_up(), _down())
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()

        assert result == 1
        assert "curl /v3/api-docs failed" in captured.out


class TestPathCountThreshold:
    def test_too_few_paths_returns_1(
        self, monkeypatch, capsys, args, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "10")
        monkeypatch.setenv("SERVER_PORT", "12345")

        body = {"paths": {"/a": {}, "/b": {}, "/c": {}}}
        run = _make_run(_up(), _ok(json.dumps(body)))
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()

        assert result == 1
        assert "paths count 3 < 10" in captured.out


class TestShutdownTimeout:
    def test_backend_still_serving_returns_1(
        self, monkeypatch, capsys, args, patch_subprocess, fast_sleep
    ):
        monkeypatch.setenv("MIN_OPENAPI_PATHS", "3")
        monkeypatch.setenv("SERVER_PORT", "12345")

        body = {"paths": {f"/p{i}": {} for i in range(3)}}
        run = _make_run(
            _up(),
            _ok(json.dumps(body)),
            _Result(),
            *([_ok("")] * 15),
            _Result(),
        )
        monkeypatch.setattr(Runner, "run", run)

        result = OpenApiFetchCommand().run(args)
        captured = capsys.readouterr()

        assert result == 1
        assert "backend still serving" in captured.out
        assert len(run.calls) == 19


class TestKillStarter:
    def test_kill_starter_invokes_pkill_with_pattern(self, monkeypatch):
        captured = []

        def fake(cmd, *args, **kwargs):
            captured.append((cmd, args, kwargs))
            return _Result()

        monkeypatch.setattr(Runner, "run", fake)
        OpenApiFetchCommand._kill_starter()

        assert captured == [
            (["pkill", "-f", "cbs.nova.starter.[S]tarterApplication"], (), {"check": False}),
        ]


class TestTail:
    def test_returns_last_n_lines(self, tmp_path):
        log = tmp_path / "log.txt"
        log.write_text("\n".join(f"line{i}" for i in range(10)) + "\n")

        buf = io.StringIO()
        OpenApiFetchCommand._tail(str(log), 3, buf)

        assert buf.getvalue().splitlines() == ["line7", "line8", "line9"]

    def test_missing_file_silent(self, tmp_path):
        buf = io.StringIO()
        OpenApiFetchCommand._tail(str(tmp_path / "missing.log"), 5, buf)

        assert buf.getvalue() == ""

    def test_fewer_lines_than_n(self, tmp_path):
        log = tmp_path / "log.txt"
        log.write_text("a\nb\n")

        buf = io.StringIO()
        OpenApiFetchCommand._tail(str(log), 5, buf)

        assert buf.getvalue() == "a\nb\n"