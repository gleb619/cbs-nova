import argparse
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.runbook_verify import RunbookVerifyCommand  # noqa: E402
from cbs_cli.config import Config  # noqa: E402
from cbs_cli.curl import Curl  # noqa: E402

FIXTURE: Path = Config.ROOT_DIR / "scripts" / "test" / "fixtures" / "runbook.md"


def _args(
    file: Path = FIXTURE,
    backend_url: str = "http://localhost:8090",
    bff_url: str = "http://localhost:3000",
    include_writes: bool = False,
    timeout: int = 5,
) -> argparse.Namespace:
    return argparse.Namespace(
        file=str(file),
        backend_url=backend_url,
        bff_url=bff_url,
        include_writes=include_writes,
        timeout=timeout,
    )


class TestExtractCurlExamples:
    def test_extracts_only_bash_fences(self):
        examples = list(RunbookVerifyCommand._extract_curl_examples(FIXTURE.read_text(encoding="utf-8")))
        commands = [e.command for e in examples]
        assert any("actuator/health" in c for c in commands)
        assert not any("```sh" in c for c in commands)
        assert len(examples) >= 6

    def test_skips_non_curl_commands(self):
        examples = list(RunbookVerifyCommand._extract_curl_examples(FIXTURE.read_text(encoding="utf-8")))
        docker = [e for e in examples if "docker" in e.command]
        make = [e for e in examples if "make logs" in e.command]
        assert len(docker) == 1 and docker[0].skip_reason == "not a curl command"
        assert len(make) == 1 and make[0].skip_reason == "not a curl command"

    def test_continuation_lines_merge(self):
        md = """```bash
# Continuation example
curl -s \\
  -H "Accept: application/json" \\
  http://localhost:8090/actuator/health | jq .components
```
"""
        examples = list(RunbookVerifyCommand._extract_curl_examples(md))
        assert len(examples) == 1
        assert examples[0].url == "http://localhost:8090/actuator/health"
        assert examples[0].method == "GET"
        assert examples[0].skip_reason is None

    def test_line_numbers_point_to_command_start(self):
        md = """```bash
# comment
curl -s http://localhost:8090/actuator/health
```
"""
        examples = list(RunbookVerifyCommand._extract_curl_examples(md))
        assert len(examples) == 1
        assert examples[0].line_number == 3


class TestSafetyClassification:
    @pytest.mark.parametrize(
        "command,expected_reason",
        [
            ("curl -s http://x", None),
            ("curl -s -X POST http://x", "write method POST"),
            ("curl -s -X PUT http://x", "write method PUT"),
            ("curl -s -X PATCH http://x", "write method PATCH"),
            ("curl -s -X DELETE http://x", "write method DELETE"),
            ("curl -s -X OPTIONS http://x", "method OPTIONS not read-only"),
            ("curl -s -X GET http://x", None),
            ("curl -s -d '{}' http://x", "request body"),
            ("curl -s --data '{}' http://x", "request body"),
            ("curl -s --data-binary '@f' http://x", "request body"),
            ("curl -s --form 'k=v' http://x", "request body"),
            ("curl -s -T f http://x", "request body"),
            ("curl -s -X GET -d '{}' http://x", "request body"),
            ("curl -s http://x/<id>", "unresolved placeholder <id>"),
        ],
    )
    def test_classification(self, command, expected_reason):
        example = RunbookVerifyCommand._parse_command(command, 1)
        assert example.skip_reason == expected_reason


class TestRunCommand:
    def test_all_read_only_pass_returns_zero(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "200")
        rc = RunbookVerifyCommand().run(_args())
        out = capsys.readouterr().out
        assert rc == 0
        assert "[ok]" in out
        assert "[skip]" in out
        assert "write method" in out or "request body" in out or "not a curl command" in out

    def test_404_counts_as_failure(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "404")
        rc = RunbookVerifyCommand().run(_args())
        out = capsys.readouterr().out
        assert rc == 1
        assert "[fail]" in out
        assert "HTTP 404" in out

    def test_non_2xx_counts_as_failure(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "500")
        rc = RunbookVerifyCommand().run(_args())
        assert rc == 1
        assert "[fail]" in capsys.readouterr().out

    def test_connection_refusal_counts_as_failure(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: None)
        rc = RunbookVerifyCommand().run(_args())
        assert rc == 1
        assert "unreachable" in capsys.readouterr().out

    def test_include_writes_executes_mutating_requests(self, monkeypatch, capsys):
        monkeypatch.setattr(Curl, "status_code", lambda url, timeout: "200")
        rc = RunbookVerifyCommand().run(_args(include_writes=True))
        out = capsys.readouterr().out
        assert rc == 0
        # With include-writes and everything mocked to 200, there should be no write skips.
        assert "write method" not in out

    def test_backend_url_override_rewrites_default_backend(self, monkeypatch):
        captured: list = []

        def fake(url, timeout):
            captured.append(url)
            return "200"

        monkeypatch.setattr(Curl, "status_code", fake)
        RunbookVerifyCommand().run(_args(backend_url="http://custom:9000"))
        assert any("custom:9000" in u for u in captured)
        assert all("localhost:8090" not in u for u in captured)

    def test_skip_only_returns_zero(self, tmp_path, capsys):
        md = tmp_path / "runbook.md"
        md.write_text("```bash\necho hello\n```\n", encoding="utf-8")
        rc = RunbookVerifyCommand().run(_args(file=md))
        out = capsys.readouterr().out
        assert rc == 0
        assert "[skip]" in out

    def test_missing_runbook_returns_one(self, capsys):
        rc = RunbookVerifyCommand().run(_args(file=Path("/no/such/runbook.md")))
        assert rc == 1
        assert "runbook not found" in capsys.readouterr().out


def test_multiline_quoted_body_does_not_crash():
    from cbs_cli.commands.runbook_verify_parser import _parse_block

    lines = [
        "curl -s -X POST http://localhost:8090/api/x -d '{",
        '  "a": 1',
        "}'",
        "curl -s http://localhost:8090/api/y",
    ]
    examples = list(_parse_block(lines, 1))
    assert len(examples) == 2
    assert examples[0].skip_reason  # write request skipped
    assert examples[1].skip_reason is None or "backend" not in (examples[1].skip_reason or "")


def test_unbalanced_quote_is_skipped_not_raised():
    from cbs_cli.commands.runbook_verify_parser import _parse_command

    ex = _parse_command("curl -s 'http://x", 1)
    assert ex.skip_reason == "unparseable shell quoting"
