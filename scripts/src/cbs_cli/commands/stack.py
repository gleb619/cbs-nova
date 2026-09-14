import argparse
import sys
from typing import List, Optional

from ..config import Config
from ..curl import Curl
from ..docker import Docker
from ..printer import Printer


class StackCommand:
    def run(self, args: argparse.Namespace) -> int:
        action = args.action
        if action == "up":
            result = Docker.run("up", "-d")
            if result.returncode != 0:
                return result.returncode
            Curl.wait_url("http://localhost:8080/realms/master", "Keycloak", "keycloak")
            Curl.wait_url("http://localhost:8000/", "Bugsink", "bugsink")
            if "temporal" in Docker.services():
                Curl.wait_url("http://localhost:8233/", "Temporal UI", "temporal")
            else:
                Printer.skip("Temporal service not declared in compose — skipping Temporal health check")
            print("\nAll services are ready. Useful URLs:")
            print("  Spring Boot   http://localhost:8090  (started by `make backend`)")
            print("  Nuxt admin UI http://localhost:3000  (started by `make frontend`)")
            print("  Keycloak      http://localhost:8080  (admin / admin)")
            print("  Bugsink       http://localhost:8000  (admin / admin)")
            print("  Temporal UI   http://localhost:8233")
            print("  Temporal gRPC localhost:7233")
            return 0
        if action == "down":
            return Docker.run("down").returncode
        if action == "logs":
            return Docker.run("logs", "-f", check=False).returncode
        if action == "clean":
            return Docker.run("down", "-v").returncode
        return self._usage()

    @staticmethod
    def _usage() -> int:
        print("usage: cbs_cli.py {up|down|logs|clean}", file=sys.stderr)
        return 1
