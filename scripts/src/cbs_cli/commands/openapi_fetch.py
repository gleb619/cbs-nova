import argparse
import json
import os
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from ..config import Config
from ..printer import Printer
from ..runner import Runner


class OpenApiFetchCommand:
    def run(self, args: argparse.Namespace) -> int:
        out = Path(args.outpath)
        min_paths_env = os.environ.get("MIN_OPENAPI_PATHS")
        if not min_paths_env:
            Printer.fail("MIN_OPENAPI_PATHS env var is required")
            return 2
        try:
            min_paths = int(min_paths_env)
        except ValueError:
            Printer.fail(f"MIN_OPENAPI_PATHS invalid: {min_paths_env}")
            return 2

        port = os.environ.get("SERVER_PORT", Config.SERVER_PORT)
        base = f"http://localhost:{port}"
        log_fd, log_path = tempfile.mkstemp(prefix="cbs-nova-openapi-boot.", suffix=".log")
        os.close(log_fd)
        raw_fd, raw_path = tempfile.mkstemp(prefix="cbs-nova-openapi-raw.", suffix=".json")
        os.close(raw_fd)

        proc = None
        try:
            cmd = [
                str(Config.GRADLEW),
                "-p",
                "backend/dsl-starter",
                ":starter-launcher:bootRun",
                "--console=plain",
            ]
            env = {**os.environ, "SERVER_PORT": port, "CBS_WEBHOOK_ENABLED": "true"}
            Printer.header(f"Booting starter-launcher headless on port {port}...")
            log_file = open(log_path, "w")
            proc = subprocess.Popen(
                cmd,
                cwd=str(Config.ROOT_DIR),
                env=env,
                stdout=log_file,
                stderr=subprocess.STDOUT,
                start_new_session=True,
            )
            log_file.close()

            up = False
            for _ in range(40):
                res = Runner.run(
                    ["curl", "-sf", "--max-time", "2", f"{base}/actuator/health"],
                    capture=True,
                    check=False,
                )
                if res.returncode == 0 and '"status":"UP"' in res.stdout:
                    up = True
                    break
                time.sleep(3)

            if not up:
                Printer.fail(f"backend not healthy after ~120s (log: {log_path})")
                self._tail(log_path, 20, sys.stderr)
                return 1

            Printer.ok(f"backend healthy ({base}/actuator/health)")

            res = Runner.run(
                ["curl", "-sf", "--max-time", "30", f"{base}/v3/api-docs"],
                capture=True,
                check=False,
            )
            if res.returncode != 0:
                Printer.fail(f"curl /v3/api-docs failed (see {log_path})")
                self._tail(log_path, 20, sys.stderr)
                return 1

            with open(raw_path, "w") as f:
                f.write(res.stdout)

            with open(raw_path) as f:
                data = json.load(f)

            out.write_text(json.dumps(data, indent=2, sort_keys=True) + "\n")
            n = len(data.get("paths", {}))
            if n < min_paths:
                Printer.fail(f"paths count {n} < {min_paths}")
                return 1
            Printer.ok(f"{n} paths (>= {min_paths})")
            Printer.ok(f"wrote {out}")

            Printer.header("Shutting down backend...")
            self._kill_starter()
            stopped = False
            for _ in range(15):
                if Runner.run(["curl", "-s", "--max-time", "1", base], capture=True, check=False).returncode != 0:
                    stopped = True
                    break
                time.sleep(1)

            if not stopped:
                Printer.fail(f"backend still serving on {base}")
                return 1
            Printer.ok("backend stopped, fetch complete")
            return 0
        finally:
            self._kill_starter()
            if proc and proc.poll() is None:
                proc.terminate()
                try:
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
            for p in (raw_path, log_path):
                try:
                    os.unlink(p)
                except FileNotFoundError:
                    pass

    @staticmethod
    def _kill_starter() -> None:
        Runner.run(["pkill", "-f", "cbs.nova.starter.[S]tarterApplication"], check=False)

    @staticmethod
    def _tail(path: str, n: int, file) -> None:
        try:
            with open(path) as f:
                lines = f.readlines()
            for line in lines[-n:]:
                file.write(line)
        except Exception:
            pass
