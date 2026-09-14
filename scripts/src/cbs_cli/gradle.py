import subprocess
from typing import List

from .config import Config
from .runner import Runner


class Gradle:
    @staticmethod
    def _run(args: List[str], check: bool = False) -> subprocess.CompletedProcess:
        return Runner.run([str(Config.GRADLEW)] + args, cwd=Config.ROOT_DIR, check=check)

    @staticmethod
    def spotless_check(build: str) -> bool:
        result = Gradle._run(
            ["-p", f"backend/{build}", "spotlessCheck", "--console=plain"], check=False
        )
        return result.returncode == 0

    @staticmethod
    def spotless_apply(build: str) -> bool:
        result = Gradle._run(
            ["-p", f"backend/{build}", "spotlessApply", "--console=plain"], check=False
        )
        return result.returncode == 0

    @staticmethod
    def publish() -> int:
        result = Gradle._run(
            ["-p", "backend/dsl-platform", "publishToMavenLocal", "-x", "test"], check=False
        )
        return result.returncode

    @staticmethod
    def bootrun_cmd() -> List[str]:
        return [
            str(Config.GRADLEW),
            "-p",
            "backend/dsl-starter",
            ":starter-launcher:bootRun",
            "-x",
            "test",
        ]
