import subprocess
from typing import List, Optional

from .runner import Runner


class Curl:
    @staticmethod
    def _base(
        url: str,
        method: str = "GET",
        data: Optional[str] = None,
        headers: Optional[List[str]] = None,
        timeout: int = 5,
        fail: bool = True,
        write_out: Optional[str] = None,
    ) -> subprocess.CompletedProcess:
        cmd = ["curl", "--silent", "--show-error", "--max-time", str(timeout)]
        if fail:
            cmd.append("--fail")
        for h in headers or []:
            cmd.extend(["-H", h])
        if data is not None:
            cmd.extend(["-X", method, "-d", data])
        else:
            cmd.extend(["-X", method])
        if write_out:
            cmd.extend(["-o", "/dev/null", "-w", write_out])
        cmd.append(url)
        return Runner.run(cmd, capture=True, check=False)

    @staticmethod
    def get(url: str, timeout: int = 5, fail: bool = True) -> str:
        result = Curl._base(url, timeout=timeout, fail=fail)
        return result.stdout

    @staticmethod
    def post(url: str, data: str, timeout: int = 10) -> bool:
        result = Curl._base(
            url,
            method="POST",
            data=data,
            headers=["Content-Type: application/json"],
            timeout=timeout,
        )
        return result.returncode == 0

    @staticmethod
    def status_code(url: str, timeout: int = 5) -> Optional[str]:
        result = Curl._base(url, timeout=timeout, fail=False, write_out="%{http_code}")
        if result.returncode == 0:
            return result.stdout.strip()
        return None

    @staticmethod
    def wait_url(url: str, name: str, service: Optional[str] = None) -> None:
        from .config import Config
        from .printer import Printer

        print(f"\n==> Waiting for {name} ({url})...")
        cmd = [
            "curl",
            "--silent",
            "--show-error",
            "--fail",
            "--retry",
            "60",
            "--retry-delay",
            "2",
            "--retry-connrefused",
            "--max-time",
            "5",
            url,
        ]
        result = Runner.run(cmd, check=False)
        if result.returncode == 0:
            Printer.ok(f"{name} is reachable")
            return
        if service:
            Printer.fail(
                f"{name} did not become ready in time. Inspect with: {' '.join(Config.COMPOSE_CMD)} logs {service}"
            )
        else:
            Printer.fail(f"{name} did not become ready in time")
        import sys

        sys.exit(1)
