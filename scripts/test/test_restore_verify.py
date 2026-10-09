import argparse
import json
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.restore_verify import RestoreVerifyCommand  # noqa: E402


def _fixture(name: str = "restore_verify_app.dump") -> Path:
    return Path(__file__).resolve().parent / "fixtures" / name


def _proc(returncode: int = 0, stdout: str = "", stderr: str = ""):
    return argparse.Namespace(returncode=returncode, stdout=stdout, stderr=stderr)


class TestRestoreVerify:
    @pytest.fixture
    def command(self, monkeypatch):
        cmd = RestoreVerifyCommand()
        calls = []

        monkeypatch.setattr(
            cmd,
            "_start_container",
            lambda cn, vn, db, port: _proc(0, stdout=cn),
        )
        monkeypatch.setattr(cmd, "_wait_for_ready", lambda cn, db: True)
        monkeypatch.setattr(
            cmd,
            "_restore_dump",
            lambda cn, db, path: _proc(0, stderr=""),
        )
        monkeypatch.setattr(
            cmd,
            "_list_public_tables",
            lambda cn, db: RestoreVerifyCommand.REQUIRED_TABLES
            + ["flyway_schema_history", "dsl_definition_snapshots"],
        )
        monkeypatch.setattr(
            cmd, "_table_row_count", lambda cn, db, table: 5
        )
        monkeypatch.setattr(cmd, "_flyway_version", lambda cn, db: 4)
        monkeypatch.setattr(
            cmd,
            "_docker",
            lambda *args, **kwargs: calls.append(args) or _proc(0, ""),
        )
        cmd.docker_calls = calls
        return cmd

    def test_happy_path(self, command, capsys):
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = command.run(args)
        out = capsys.readouterr().out

        assert result == 0
        assert "RESTORE VERIFY OK" in out
        assert "6 tables, flyway V4" in out
        assert any(c[:2] == ("rm", "-f") for c in command.docker_calls)
        assert any(
            len(c) >= 2 and c[0] == "volume" and c[1] == "rm"
            for c in command.docker_calls
        )

    def test_tampered_dump_fails_and_cleans_up(self, command, monkeypatch, capsys):
        monkeypatch.setattr(
            command,
            "_restore_dump",
            lambda cn, db, path: _proc(1, stderr="pg_restore: corrupt archive"),
        )
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = command.run(args)
        out = capsys.readouterr().out

        assert result == 1
        assert "RESTORE VERIFY FAILED" in out
        assert "pg_restore failed" in out
        assert any(c[:2] == ("rm", "-f") for c in command.docker_calls)
        assert any(
            len(c) >= 2 and c[0] == "volume" and c[1] == "rm"
            for c in command.docker_calls
        )

    def test_missing_required_table(self, command, monkeypatch, capsys):
        monkeypatch.setattr(
            command, "_list_public_tables", lambda cn, db: ["dsl_runs", "dsl_audit", "dsl_events"]
        )
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = command.run(args)
        out = capsys.readouterr().out

        assert result == 1
        assert "missing required tables: dsl_api_keys" in out

    def test_empty_core_table(self, command, monkeypatch, capsys):
        monkeypatch.setattr(
            command,
            "_table_row_count",
            lambda cn, db, table: 0 if table == "dsl_runs" else 5,
        )
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = command.run(args)
        out = capsys.readouterr().out

        assert result == 1
        assert "table dsl_runs has no rows" in out

    def test_flyway_version_mismatch(self, command, monkeypatch, capsys):
        monkeypatch.setattr(command, "_flyway_version", lambda cn, db: 3)
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = command.run(args)
        out = capsys.readouterr().out

        assert result == 1
        assert "flyway version mismatch: got V3, expected V4" in out

    def test_json_output(self, command, capsys):
        args = argparse.Namespace(
            file=str(_fixture()),
            service="app",
            expect_flyway_version=None,
            json=True,
        )
        result = command.run(args)
        out = capsys.readouterr().out.strip()

        assert result == 0
        verdict = json.loads(out)
        assert verdict["ok"] is True
        assert verdict["tables"] == 6
        assert verdict["flyway_version"] == 4
        assert verdict["service"] == "app"
        assert verdict["db"] == "nova"
        assert "RESTORE VERIFY OK" not in out

    def test_missing_dump_file(self, capsys):
        args = argparse.Namespace(
            file=str(_fixture("no-such-dump.dump")),
            service="app",
            expect_flyway_version=None,
            json=False,
        )
        result = RestoreVerifyCommand().run(args)

        assert result == 1
        assert "dump file not found" in capsys.readouterr().out

    def test_service_inference_for_temporal(self, command, monkeypatch):
        recorded = []
        monkeypatch.setattr(
            command,
            "_start_container",
            lambda cn, vn, db, port: recorded.append(db) or _proc(0, stdout=cn),
        )
        fixture = _fixture().parent / "temporal-20261007-120000.dump"
        fixture.write_text("x")
        try:
            args = argparse.Namespace(
                file=str(fixture),
                service=None,
                expect_flyway_version=None,
                json=False,
            )
            command.run(args)
            assert recorded == ["temporal"]
        finally:
            fixture.unlink(missing_ok=True)
