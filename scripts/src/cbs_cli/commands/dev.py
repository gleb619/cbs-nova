import argparse
import os
import signal
import subprocess
import sys
import threading
import time
from pathlib import Path
from typing import Dict, Optional, Tuple

from ..config import Config
from ..gradle import Gradle


class DevCommand:
    def run(self, args: argparse.Namespace) -> int:
        log_dir = Config.ROOT_DIR / ".dev-logs"
        log_dir.mkdir(exist_ok=True)
        backend_log = log_dir / "backend.log"
        frontend_log = log_dir / "frontend.log"

        backend_env = {"SERVER_PORT": Config.SERVER_PORT}
        backend_cmd = [str(Config.GRADLEW), "-p", "backend/dsl-starter", ":starter-launcher:bootRun", "-x", "test"]
        frontend_cmd = ["pnpm", "dev"]

        print(f"Backend  log: {backend_log}")
        print(f"Frontend log: {frontend_log}")
        print("Press Ctrl+C to stop both.\n")

        procs: Dict[str, Tuple[subprocess.Popen, Path]] = {}
        first_exit = threading.Event()

        def start(name: str, cmd, cwd: Path, log_path: Path, env: Optional[Dict[str, str]] = None):
            log_file = open(log_path, "w")
            proc = subprocess.Popen(
                cmd,
                cwd=str(cwd),
                env={**os.environ, **(env or {})},
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1,
                start_new_session=True,
            )
            procs[name] = (proc, log_path)
            threading.Thread(target=self._stream, args=(proc, log_file, name), daemon=True).start()
            threading.Thread(target=self._watch, args=(proc, name, first_exit), daemon=True).start()

        start("backend", backend_cmd, Config.ROOT_DIR, backend_log, backend_env)
        start("frontend", frontend_cmd, Config.ROOT_DIR / "frontend", frontend_log)

        try:
            first_exit.wait()
        except KeyboardInterrupt:
            pass
        finally:
            self._shutdown(procs)

        rc = 0
        for proc, _ in procs.values():
            rc = max(rc, proc.poll() or 0)

        print(f"\nA dev process exited (status {rc}). Last log lines:", file=sys.stderr)
        print("--- backend (last 20 lines) ---", file=sys.stderr)
        self._tail(backend_log, 20, file=sys.stderr)
        print("--- frontend (last 20 lines) ---", file=sys.stderr)
        self._tail(frontend_log, 20, file=sys.stderr)
        return rc

    @staticmethod
    def _stream(proc: subprocess.Popen, log_file, name: str) -> None:
        try:
            for line in proc.stdout:
                log_file.write(line)
                log_file.flush()
                sys.stdout.write(f"[{name}]  {line}")
        except Exception:
            pass
        finally:
            proc.stdout.close()
            log_file.close()

    @staticmethod
    def _watch(proc: subprocess.Popen, name: str, event: threading.Event) -> None:
        proc.wait()
        event.set()

    @staticmethod
    def _shutdown(procs: Dict[str, Tuple[subprocess.Popen, Path]]) -> None:
        for proc, _ in procs.values():
            try:
                pgid = os.getpgid(proc.pid)
                os.killpg(pgid, signal.SIGTERM)
            except ProcessLookupError:
                pass
        time.sleep(0.3)
        for proc, _ in procs.values():
            if proc.poll() is None:
                try:
                    pgid = os.getpgid(proc.pid)
                    os.killpg(pgid, signal.SIGKILL)
                except ProcessLookupError:
                    pass

    @staticmethod
    def _tail(path: Path, n: int, file) -> None:
        try:
            with open(path) as f:
                lines = f.readlines()
            for line in lines[-n:]:
                file.write(line)
        except Exception:
            pass
