import argparse
import json
import sys
from pathlib import Path
from types import SimpleNamespace

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.seed import SeedCommand  # noqa: E402
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
            seen["kwargs"] = kwargs
            return _down()

        _patch(monkeypatch, base=base, get=lambda *a: pytest.fail("get not called"), post=lambda *a: pytest.fail("post not called"))
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 1
        assert seen["url"] == "http://bff"
        assert seen["kwargs"].get("fail") is False
        assert "BFF not reachable" in out

    def test_already_present_returns_0_and_no_post(self, monkeypatch, capsys):
        body = json.dumps([{"name": "seed-hello-world", "status": "Published"}])
        posts = []

        def post(url, data, timeout):
            posts.append((url, data, timeout))
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: body,
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert posts == []
        assert "already present" in out

    def test_save_fails_returns_1(self, monkeypatch, capsys):
        posts = []

        def post(url, data, timeout):
            posts.append((url, timeout))
            return False

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 1
        assert len(posts) == 1
        assert posts[0][0].endswith("/api/v1/dsl/drafts/seed-hello-world/save")
        assert "save draft seed-hello-world" in out

    def test_publish_fails_returns_1(self, monkeypatch, capsys):
        posts = []

        def post(url, data, timeout):
            posts.append(url)
            return False  # both save and publish fail; saves returncode check still triggers return 1

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 1
        # first POST was save; because save fails the function returns immediately.
        assert len(posts) == 1
        assert posts[0].endswith("/api/v1/dsl/drafts/seed-hello-world/save")

    def test_publish_failure_after_save_returns_1(self, monkeypatch, capsys):
        # explicitly fail publish only
        def post(url, data, timeout):
            if "/publish" in url:
                return False
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 1
        assert "publish seed-hello-world" in out

    def test_happy_path_three_posts_return_0(self, monkeypatch, capsys):
        posts = []

        def post(url, data, timeout):
            posts.append((url, data, timeout))
            return True

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert len(posts) == 3
        urls = [u for u, _, _ in posts]
        assert urls[0].endswith("/api/v1/dsl/drafts/seed-hello-world/save")
        assert urls[1].endswith("/api/v1/dsl/drafts/seed-hello-world/publish")
        assert urls[2].endswith("/api/v1/dsl/run/seed-hello-world")
        # save body carries draft
        save_body = json.loads(posts[0][1])
        assert save_body["status"] == "Draft"
        pub_body = json.loads(posts[1][1])
        assert pub_body["status"] == "Published"
        run_body = json.loads(posts[2][1])
        assert run_body == {"body": {}, "metadata": {"source": "make seed"}}
        assert "triggered sample run for seed-hello-world" in out

    def test_run_post_fails_still_returns_0(self, monkeypatch, capsys):
        posts = []

        def post(url, data, timeout):
            posts.append(url)
            return "/run/" not in url  # save + publish ok, run fails

        _patch(
            monkeypatch,
            base=lambda url, kwargs: _ok(),
            get=lambda url, timeout, fail: json.dumps([]),
            post=post,
        )
        result = SeedCommand().run(_args())
        out = capsys.readouterr().out
        assert result == 0
        assert len(posts) == 3
        assert "could not trigger sample run for seed-hello-world" in out


class TestDefinitionExists:
    def test_empty_body_miss(self):
        assert SeedCommand._definition_exists("", "seed-hello-world") is False

    def test_none_body_miss(self):
        assert SeedCommand._definition_exists(None, "seed-hello-world") is False

    def test_nested_dict_hit(self):
        body = json.dumps({"items": [{"name": "other"}, {"nested": {"name": "seed-hello-world"}}]})
        assert SeedCommand._definition_exists(body, "seed-hello-world") is True

    def test_nested_list_hit(self):
        body = json.dumps([[[{"name": "seed-hello-world"}]]])
        assert SeedCommand._definition_exists(body, "seed-hello-world") is True

    def test_miss(self):
        body = json.dumps({"items": [{"name": "other"}]})
        assert SeedCommand._definition_exists(body, "seed-hello-world") is False

    def test_top_level_dict_miss(self):
        # no "name" key at top level, no recursion value contains name
        body = json.dumps({"foo": "bar", "items": [1, 2, 3]})
        assert SeedCommand._definition_exists(body, "seed-hello-world") is False

    def test_invalid_json_substring_fallback_hit(self):
        body = 'oops not json but "name":"seed-hello-world" appears here'
        assert SeedCommand._definition_exists(body, "seed-hello-world") is True

    def test_invalid_json_substring_fallback_miss(self):
        body = 'completely unrelated text without the literal'
        assert SeedCommand._definition_exists(body, "seed-hello-world") is False


class TestFindName:
    def test_dict_top_hit(self):
        assert SeedCommand._find_name({"name": "seed-hello-world"}, "seed-hello-world") is True

    def test_dict_nested_hit(self):
        data = {"items": [{"name": "other"}, {"name": "seed-hello-world"}]}
        assert SeedCommand._find_name(data, "seed-hello-world") is True

    def test_list_hit(self):
        data = [{"name": "x"}, [{"name": "seed-hello-world"}]]
        assert SeedCommand._find_name(data, "seed-hello-world") is True

    def test_miss(self):
        data = [{"a": 1}, {"b": [{"c": "v"}]}]
        assert SeedCommand._find_name(data, "seed-hello-world") is False

    def test_scalar_miss(self):
        assert SeedCommand._find_name(42, "seed-hello-world") is False
        assert SeedCommand._find_name("seed-hello-world", "seed-hello-world") is False
        assert SeedCommand._find_name(None, "seed-hello-world") is False