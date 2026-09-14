import os
import subprocess
from pathlib import Path
from typing import Dict, List, Optional


class Runner:
    @staticmethod
    def run(
        cmd: List[str],
        cwd: Optional[Path] = None,
        env: Optional[Dict[str, str]] = None,
        check: bool = False,
        capture: bool = False,
        timeout: Optional[int] = None,
    ) -> subprocess.CompletedProcess:
        merged_env = {**os.environ, **(env or {})}
        return subprocess.run(
            cmd,
            cwd=str(cwd) if cwd else None,
            env=merged_env,
            check=check,
            capture_output=capture,
            text=True,
            timeout=timeout,
        )

    @staticmethod
    def exec(cmd: List[str], cwd: Optional[Path] = None, env: Optional[Dict[str, str]] = None) -> None:
        merged_env = {**os.environ, **(env or {})}
        if cwd:
            os.chdir(str(cwd))
        os.execvpe(cmd[0], cmd, merged_env)
