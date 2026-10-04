import argparse
import os
import sys
from pathlib import Path

from ..config import Config
from ..curl import Curl
from ..docker import Docker
from ..printer import Printer
from ..runner import Runner


class QualityCommand:
    """Manage local code-quality infrastructure and scans."""

    def __init__(self) -> None:
        self.compose_file = Config.ROOT_DIR / "app" / "compose" / "quality.yml"

    def run(self, args: argparse.Namespace) -> int:
        action = args.quality_action
        if action == "up":
            return self._up()
        if action == "down":
            return self._down()
        if action == "scan":
            return self._scan()
        return self._usage()

    def _up(self) -> int:
        Printer.header("Starting quality infrastructure (SonarQube, Dependency-Track)...")
        result = Docker.run("-f", str(self.compose_file), "up", "-d")
        if result.returncode != 0:
            return result.returncode
        Curl.wait_url("http://localhost:9000/api/system/status", "SonarQube", "sonarqube")
        Curl.wait_url("http://localhost:8081/api/version", "Dependency-Track API", "dtrack-apiserver")
        Printer.ok("Quality infrastructure is ready")
        print("\nURLs:")
        print("  SonarQube              http://localhost:9000  (admin / admin)")
        print("  Dependency-Track UI    http://localhost:8082")
        return 0

    def _down(self) -> int:
        Printer.header("Stopping quality infrastructure...")
        return Docker.run("-f", str(self.compose_file), "down").returncode

    def _scan(self) -> int:
        Printer.header("Running quality scan...")

        for build in Config.BACKEND_BUILDS:
            Printer.header(f"Quality checks for {build}...")
            result = Runner.run(
                [str(Config.GRADLEW), "-p", f"backend/{build}", "spotlessCheck", "spotbugsMain", "--console=plain"],
                cwd=Config.ROOT_DIR,
                check=False,
            )
            if result.returncode != 0:
                Printer.fail(f"{build} quality checks failed")
                return result.returncode
            Printer.ok(f"{build} spotlessCheck + spotbugsMain")

        Printer.header("Generating aggregate SBOM...")
        result = Runner.run(
            [str(Config.GRADLEW), "-p", "backend/dsl-starter", "cyclonedxBom", "--console=plain"],
            cwd=Config.ROOT_DIR,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail("SBOM generation failed")
            return result.returncode
        Printer.ok("aggregate SBOM generated")

        Printer.header("Running SonarQube analysis...")
        sonar_host = os.environ.get("SONAR_HOST_URL", "http://localhost:9000")
        result = Runner.run(
            [str(Config.GRADLEW), "-p", "backend/dsl-starter", "sonar", f"-Dsonar.host.url={sonar_host}", "--console=plain"],
            cwd=Config.ROOT_DIR,
            check=False,
        )
        if result.returncode != 0:
            Printer.fail("SonarQube analysis failed")
            return result.returncode
        Printer.ok("SonarQube analysis published")

        Printer.header("Uploading SBOM to Dependency-Track...")
        sbom_path = Config.ROOT_DIR / "backend" / "dsl-starter" / "build" / "reports" / "cyclonedx" / "aggregate-bom.json"
        dtrack_url = os.environ.get("DTRACK_URL", "http://localhost:8081")
        dtrack_api_key = os.environ.get("DTRACK_API_KEY", "odt_admin")
        if not sbom_path.exists():
            Printer.warn(f"SBOM not found at {sbom_path}, skipping upload")
        else:
            result = Runner.run(
                [
                    "curl", "-sS", "-X", "PUT",
                    "-H", f"X-Api-Key: {dtrack_api_key}",
                    "-H", "Content-Type: multipart/form-data",
                    "-F", "autoCreate=true",
                    "-F", "projectName=cbs-nova-backend",
                    "-F", "projectVersion=1.0.0",
                    "-F", f"bom=@{sbom_path}",
                    f"{dtrack_url}/api/v1/bom",
                ],
                check=False,
            )
            if result.returncode != 0:
                Printer.warn("SBOM upload to Dependency-Track failed")
            else:
                Printer.ok("SBOM uploaded to Dependency-Track")

        print("\n==> Quality scan complete")
        print("  SonarQube:           http://localhost:9000")
        print("  Dependency-Track UI: http://localhost:8082")
        return 0

    @staticmethod
    def _usage() -> int:
        print("usage: cbs_cli.py quality {up|down|scan}", file=sys.stderr)
        return 1

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        subparsers = parser.add_subparsers(dest="quality_action", required=True)
        subparsers.add_parser("up", help="Start SonarQube and Dependency-Track containers")
        subparsers.add_parser("down", help="Stop quality containers")
        subparsers.add_parser("scan", help="Run linters, SpotBugs, SBOM, SonarQube, and Dependency-Track upload")
