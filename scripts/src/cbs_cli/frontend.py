import subprocess
from typing import List, Optional

from .config import Config
from .runner import Runner


class Frontend:
    @staticmethod
    def _pnpm(args: List[str], check: bool = False) -> subprocess.CompletedProcess:
        return Runner.run(["pnpm"] + args, cwd=Config.ROOT_DIR / "frontend", check=check)

    @staticmethod
    def dev() -> int:
        result = Frontend._pnpm(["dev"], check=False)
        return result.returncode

    @staticmethod
    def lint() -> int:
        result = Frontend._pnpm(["lint"], check=False)
        return result.returncode

    @staticmethod
    def format() -> int:
        result = Frontend._pnpm(["format"], check=False)
        return result.returncode
