import argparse
import sys

from ..config import Config
from ..frontend import Frontend
from ..gradle import Gradle
from ..runner import Runner


class ServicesCommand:
    def run(self, args: argparse.Namespace) -> int:
        cmd = args.service
        if cmd == "backend":
            Runner.exec(
                Gradle.bootrun_cmd(),
                cwd=Config.ROOT_DIR,
                env={"SERVER_PORT": Config.SERVER_PORT},
            )
        if cmd == "frontend":
            Runner.exec(["pnpm", "dev"], cwd=Config.ROOT_DIR / "frontend")
        if cmd == "publish":
            return Gradle.publish()
        return self._usage()

    @staticmethod
    def _usage() -> int:
        print("usage: cbs_cli.py {backend|frontend|publish}", file=sys.stderr)
        return 1
