import subprocess
from typing import List

from .config import Config
from .runner import Runner


class Docker:
    @staticmethod
    def _cmd(*args: str) -> List[str]:
        return Config.COMPOSE_CMD + list(args)

    @staticmethod
    def run(*args: str, capture: bool = False, check: bool = False) -> subprocess.CompletedProcess:
        return Runner.run(Docker._cmd(*args), capture=capture, check=check)

    @staticmethod
    def is_stack_running() -> bool:
        result = Docker.run("ps", "-q", capture=True, check=False)
        return bool(result.stdout.strip())

    @staticmethod
    def services() -> List[str]:
        result = Docker.run("config", "--services", capture=True, check=False)
        return [s.strip() for s in result.stdout.splitlines() if s.strip()]
