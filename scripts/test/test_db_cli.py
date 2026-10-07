import argparse
import sys
from pathlib import Path

import pytest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.db import DbCommand  # noqa: E402
from cbs_cli.docker import Docker  # noqa: E402
from cbs_cli.runner import Runner  # noqa: E402


class TestDbBackup:

    @staticmethod
    def _fake_datetime():
        class FakeNow:
            def strftime(self, fmt):
                return "20261007-120000"

        class FakeDatetime:
            @staticmethod
            def now():
                return FakeNow()

        return FakeDatetime()
    def test_backup_creates_timestamped_dumps(self, monkeypatch, tmp_path, capsys):
        calls = []

        def fake_docker_run(*args, capture=False, check=False, **kwargs):
            calls.append(list(args))
            stdout = "fake-dump-bytes"
            return argparse.Namespace(returncode=0, stdout=stdout, stderr="")

        monkeypatch.setattr(Docker, "run", fake_docker_run)
        monkeypatch.setattr("cbs_cli.commands.db.datetime", self._fake_datetime())
        monkeypatch.setattr(DbCommand, "_find_vhs_volumes", lambda: [])

        out = tmp_path / "backups"
        args = argparse.Namespace(db_action="backup", out=str(out), services="app,temporal")
        result = DbCommand().run(args)
        out_str = capsys.readouterr().out

        assert result == 0
        assert len(calls) == 2
        assert calls[0] == ["exec", "-T", "postgres", "pg_dump", "-U", "nova", "-d", "nova", "-Fc"]
        assert calls[1] == ["exec", "-T", "postgres", "pg_dump", "-U", "temporal", "-d", "temporal", "-Fc"]

        app_dump = out / "app-20261007-120000.dump"
        temporal_dump = out / "temporal-20261007-120000.dump"
        assert app_dump.exists() and app_dump.read_text() == "fake-dump-bytes"
        assert temporal_dump.exists() and temporal_dump.read_text() == "fake-dump-bytes"
        assert str(app_dump) in out_str
        assert str(temporal_dump) in out_str

    def test_backup_rejects_unknown_service(self, monkeypatch, tmp_path, capsys):
        monkeypatch.setattr(Docker, "run", lambda *args, **kwargs: argparse.Namespace(returncode=0, stdout="x", stderr=""))
        monkeypatch.setattr(DbCommand, "_find_vhs_volumes", lambda: [])

        args = argparse.Namespace(db_action="backup", out=str(tmp_path / "backups"), services="app,unknown")
        result = DbCommand().run(args)
        assert result == 1
        assert "unknown service 'unknown'" in capsys.readouterr().out

    def test_backup_prints_vhs_tar_command(self, monkeypatch, tmp_path, capsys):
        calls = []

        def fake_docker_run(*args, capture=False, check=False, **kwargs):
            calls.append(list(args))
            return argparse.Namespace(returncode=0, stdout="dump", stderr="")

        monkeypatch.setattr(Docker, "run", fake_docker_run)
        monkeypatch.setattr("cbs_cli.commands.db.datetime", self._fake_datetime())
        monkeypatch.setattr(
            DbCommand,
            "_find_vhs_volumes",
            lambda: [("spring-app", "/var/cbs-nova/vhs", "cbs-nova-vhs-data")],
        )

        out = tmp_path / "backups"
        args = argparse.Namespace(db_action="backup", out=str(out), services="app")
        result = DbCommand().run(args)
        out_str = capsys.readouterr().out

        assert result == 0
        assert "VHS tape volumes detected" in out_str
        assert "docker run --rm -v cbs-nova-vhs-data:/vhs-data" in out_str
        assert "spring-app-vhs-20261007-120000.tgz" in out_str


class TestDbRestore:
    def test_restore_refuses_without_yes(self, monkeypatch, tmp_path, capsys):
        dump = tmp_path / "app-20261007-120000.dump"
        dump.write_text("dump")
        args = argparse.Namespace(db_action="restore", file=str(dump), service="app", yes=False)

        result = DbCommand().run(args)
        out = capsys.readouterr().out

        assert result == 1
        assert "will overwrite the 'nova' database" in out
        assert "--yes" in out

    def test_restore_runs_pg_restore(self, monkeypatch, tmp_path, capsys):
        dump = tmp_path / "app-20261007-120000.dump"
        dump.write_bytes(b"dump-bytes")

        calls = []

        def fake_runner_run(cmd, **kwargs):
            calls.append((cmd, kwargs.get("input")))
            return argparse.Namespace(returncode=0, stdout="", stderr="")

        monkeypatch.setattr(Runner, "run", fake_runner_run)

        args = argparse.Namespace(db_action="restore", file=str(dump), service="app", yes=True)
        result = DbCommand().run(args)

        assert result == 0
        assert len(calls) == 1
        cmd, inp = calls[0]
        assert cmd[:2] == ["docker", "compose"]
        assert "exec" in cmd
        exec_idx = cmd.index("exec")
        assert cmd[exec_idx:] == [
            "exec",
            "-T",
            "postgres",
            "pg_restore",
            "-U",
            "postgres",
            "-d",
            "nova",
            "-Fc",
            "--clean",
            "--if-exists",
        ]
        assert inp == b"dump-bytes"

    def test_restore_fails_when_dump_missing(self, tmp_path, capsys):
        args = argparse.Namespace(db_action="restore", file=str(tmp_path / "missing.dump"), service="app", yes=True)
        result = DbCommand().run(args)
        assert result == 1
        assert "dump file not found" in capsys.readouterr().out

    def test_restore_temporal_service(self, monkeypatch, tmp_path):
        dump = tmp_path / "temporal-20261007-120000.dump"
        dump.write_bytes(b"dump")

        calls = []
        monkeypatch.setattr(Runner, "run", lambda cmd, **kwargs: calls.append(cmd) or argparse.Namespace(returncode=0, stdout="", stderr=""))

        args = argparse.Namespace(db_action="restore", file=str(dump), service="temporal", yes=True)
        result = DbCommand().run(args)
        assert result == 0
        cmd = calls[0]
        assert "-d" in cmd and "temporal" in cmd
        assert "exec" in cmd


class TestFindVhsVolumes:
    def test_detects_short_form_vhs_mount(self, monkeypatch):
        config_json = """{
            "services": {
                "spring-app": {
                    "volumes": [
                        "cbs-nova-vhs-data:/var/cbs-nova/vhs:rw"
                    ]
                }
            }
        }"""
        monkeypatch.setattr(
            Docker,
            "run",
            lambda *args, capture=False, check=False, **kwargs: argparse.Namespace(
                returncode=0, stdout=config_json, stderr=""
            ),
        )
        volumes = DbCommand._find_vhs_volumes()
        assert volumes == [("spring-app", "/var/cbs-nova/vhs", "cbs-nova-vhs-data")]

    def test_detects_dict_form_vhs_mount(self, monkeypatch):
        config_json = """{
            "services": {
                "spring-app": {
                    "volumes": [
                        {"source": "vhs-tapes", "target": "/var/cbs-nova/vhs"}
                    ]
                }
            }
        }"""
        monkeypatch.setattr(
            Docker,
            "run",
            lambda *args, capture=False, check=False, **kwargs: argparse.Namespace(
                returncode=0, stdout=config_json, stderr=""
            ),
        )
        volumes = DbCommand._find_vhs_volumes()
        assert volumes == [("spring-app", "/var/cbs-nova/vhs", "vhs-tapes")]

    def test_returns_empty_on_bad_config(self, monkeypatch):
        monkeypatch.setattr(
            Docker,
            "run",
            lambda *args, capture=False, check=False, **kwargs: argparse.Namespace(
                returncode=1, stdout="", stderr=""
            ),
        )
        assert DbCommand._find_vhs_volumes() == []
