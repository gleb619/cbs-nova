import argparse
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.seed_history import SeedHistoryCommand  # noqa: E402
from cbs_cli.config import Config  # noqa: E402
from cbs_cli.curl import Curl  # noqa: E402


def _args():
    return argparse.Namespace()


def _ok():
    return SimpleNamespace(returncode=0)


def _down():
    return SimpleNamespace(returncode=7)


def _patch(monkeypatch, *, base, get, post):
    monkeypatch.setattr(Config, "BFF_BASE_URL", "http://bff")
    monkeypatch.setattr(Curl, "_base", lambda url, **kwargs: base(url, kwargs))
    monkeypatch.setattr(Curl, "get", lambda url, timeout, fail: get(url, timeout, fail))
    monkeypatch.setattr(Curl, "post", lambda url, data, timeout: post(url, data, timeout))


class TestRun:
    def test_bff_unreachable_returns_1(self, monkeypatch, capsys):
        seen = {}

        def base(url, kwargs):
            seen["url"] = url
            return _down()

        _patch(
            monkeypatch,
            base=base,
            get=lambda *a: pytest.fail("get not called"),
            post=lambda *a: pytest.fail("post not called"),
        )
        result = SeedHistoryCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 1
        assert seen["url"] == "http://bff"
        assert "BFF not reachable" in out

    def test_no_existing_runs_seeds_5(self, monkeypatch, capsys):
        posts = []

        def post(url, data, timeout):
            posts.append((url, json.loads(data), timeout))
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedHistoryCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert len(posts) == 5
        tags = [p[1]["metadata"]["tag"] for p in posts]
        assert tags == ["seed-history-1", "seed-history-2", "seed-history-3", "seed-history-4", "seed-history-5"]
        for url, _, _ in posts:
            assert url.endswith("/api/v1/dsl/run/seed-hello-world")
        assert "Seeding history complete (up to 5 runs)." in out

    def test_cap_reached_skips_posting(self, monkeypatch, capsys):
        txs = json.dumps([
            {"name": "seed-history-1"},
            {"name": "seed-history-2"},
            {"name": "seed-history-3"},
            {"name": "seed-history-4"},
            {"name": "seed-history-5"},
        ])
        posts = []

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: txs,
            post=lambda url, data, timeout: posts.append(url) or True,
        )
        result = SeedHistoryCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert posts == []
        assert "already at cap (5)" in out

    def test_partial_existing_seeds_remainder(self, monkeypatch, capsys):
        # 2 existing → seed 3 more
        txs = json.dumps([
            {"name": "seed-history-1"},
            {"name": "seed-history-2"},
        ])
        posts = []

        def post(url, data, timeout):
            posts.append(json.loads(data)["metadata"]["tag"])
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: txs,
            post=post,
        )
        result = SeedHistoryCommand().run(_args())
        assert result == 0
        assert posts == ["seed-history-3", "seed-history-4", "seed-history-5"]

    def test_invalid_json_uses_substring_count(self, monkeypatch, capsys):
        # non-JSON body with literal "seed-history-" substrings → regex fallback
        txs = 'not json seed-history-1 seed-history-2 seed-history-3'
        posts = []

        def post(url, data, timeout):
            posts.append(json.loads(data)["metadata"]["tag"])
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: txs,
            post=post,
        )
        result = SeedHistoryCommand().run(_args())
        assert result == 0
        # 3 hits → cap not reached → seed 2 more
        assert posts == ["seed-history-4", "seed-history-5"]

    def test_empty_tx_body_seeds_all_5(self, monkeypatch, capsys):
        posts = []

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: "",
            post=lambda url, data, timeout: posts.append(url) or True,
        )
        result = SeedHistoryCommand().run(_args())
        assert result == 0
        assert len(posts) == 5

    def test_post_failure_warns_but_returns_0(self, monkeypatch, capsys):
        posts_status = []

        def post(url, data, timeout):
            posts_status.append(url)
            return False  # all fail

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedHistoryCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert len(posts_status) == 5
        assert "could not be triggered" in out

    def test_mixed_post_results(self, monkeypatch, capsys):
        # every other call fails; loop continues regardless
        counter = {"n": 0}

        def post(url, data, timeout):
            counter["n"] += 1
            return counter["n"] % 2 == 1

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedHistoryCommand().run(_args())
        assert result == 0


class TestAllItems:
    def test_dict_with_name(self):
        data = {"name": "seed-history-1", "other": "x"}
        assert SeedHistoryCommand._all_items(data) == [data]

    def test_dict_without_name_recurses(self):
        nested = {"name": "inner", "v": 1}
        data = {"wrapper": {"deep": nested}}
        # outer dict has no "name" → recurse values → inner dict returned
        assert SeedHistoryCommand._all_items(data) == [nested]

    def test_list_recurses(self):
        nested = {"name": "x"}
        data = [nested, [{"name": "y"}]]
        assert SeedHistoryCommand._all_items(data) == [nested, {"name": "y"}]

    def test_scalar_returns_empty(self):
        assert SeedHistoryCommand._all_items(42) == []
        assert SeedHistoryCommand._all_items("string") == []
        assert SeedHistoryCommand._all_items(None) == []

    def test_empty_collections(self):
        assert SeedHistoryCommand._all_items([]) == []
        assert SeedHistoryCommand._all_items({}) == []

    def test_nested_mixed(self):
        target1 = {"name": "a", "k": 1}
        target2 = {"name": "b", "k": 2}
        data = {"top": [target1, {"sub": [target2, "scalar", 99]}]}
        result = SeedHistoryCommand._all_items(data)
        assert target1 in result and target2 in result
        # dicts without "name" are not yielded themselves
        assert len(result) == 2

    def test_dict_with_name_wins_immediately(self):
        # outer dict has "name" → returned as-is, children ignored
        outer = {"name": "outer", "child": {"name": "inner"}}
        assert SeedHistoryCommand._all_items(outer) == [outer]