import argparse
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.loadtest import LoadtestCommand  # noqa: E402
from cbs_cli.config import Config  # noqa: E402
from cbs_cli.docker import Docker  # noqa: E402


class TestLoadtestRun:
    def _patch_stack_and_env(self, monkeypatch):
        monkeypatch.setattr(Docker, "is_stack_running", lambda: True)
        monkeypatch.setenv("DURATION", "1")
        monkeypatch.setenv("CONCURRENCY", "1")
        monkeypatch.setenv("RPS", "1")
        monkeypatch.setenv("MAX_P99_MS", "2000")
        monkeypatch.setenv("MAX_ERROR_RATE", "5")

    def test_skip_when_stack_not_running(self, monkeypatch, capsys):
        monkeypatch.setattr(Docker, "is_stack_running", lambda: False)
        result = LoadtestCommand().run(argparse.Namespace())
        out = capsys.readouterr().out
        assert result == 0
        assert "skipping load test" in out

    def test_all_endpoints_pass(self, monkeypatch, capsys):
        self._patch_stack_and_env(monkeypatch)

        samples = {
            Config.BFF_BASE_URL + ep: [(200, 0.010 + i * 0.001)]
            for i, ep in enumerate(LoadtestCommand.ENDPOINTS)
        }

        def fake_collect(url, *args, **kwargs):
            return samples.get(url, [(200, 0.010)])

        monkeypatch.setattr(LoadtestCommand, "_collect", fake_collect)

        result = LoadtestCommand().run(argparse.Namespace())
        out = capsys.readouterr().out
        assert result == 0
        assert "All endpoints within thresholds." in out
        for ep in LoadtestCommand.ENDPOINTS:
            assert ep in out
        assert "/api/v1/executions/stats/timeseries?windowHours=24&bucketMinutes=60" in out

    def test_p99_exceeds_threshold_fails(self, monkeypatch, capsys):
        self._patch_stack_and_env(monkeypatch)

        def fake_collect(url, *args, **kwargs):
            return [(200, 3.0)]

        monkeypatch.setattr(LoadtestCommand, "_collect", fake_collect)

        result = LoadtestCommand().run(argparse.Namespace())
        out = capsys.readouterr().out
        assert result == 1
        assert "p99=3000ms exceeds MAX_P99_MS=2000ms" in out
        assert "endpoint(s) failed load-test thresholds" in out

    def test_error_rate_exceeds_threshold_fails(self, monkeypatch, capsys):
        self._patch_stack_and_env(monkeypatch)

        def fake_collect(url, *args, **kwargs):
            return [(500, 0.01)]

        monkeypatch.setattr(LoadtestCommand, "_collect", fake_collect)

        result = LoadtestCommand().run(argparse.Namespace())
        out = capsys.readouterr().out
        assert result == 1
        assert "err=100% exceeds MAX_ERROR_RATE=5%" in out
        assert "endpoint(s) failed load-test thresholds" in out


class TestPercentiles:
    def test_empty_returns_zeros(self):
        assert LoadtestCommand._percentiles([], 50, 95, 99) == (0, 0, 0)

    def test_percentile_indexing(self):
        times = sorted([0.010, 0.020, 0.030, 0.100, 0.200])
        p50, p95, p99 = LoadtestCommand._percentiles(times, 50, 95, 99)
        assert p50 == 30
        assert p95 == 200
        assert p99 == 200
