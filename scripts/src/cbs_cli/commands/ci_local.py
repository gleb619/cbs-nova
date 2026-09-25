import argparse
import copy
import shutil
from pathlib import Path
from typing import Any, List

import yaml

from ..config import Config
from ..printer import Printer
from ..runner import Runner

# Why this command exists
# -----------------------
# wrkflw's emulation runtime starts every `run:` step in a fresh empty temp
# directory, although GITHUB_WORKSPACE points at the checkout. Each run step is a
# separate shell, so the generated workflow must prefix every run command with a
# cd. Its Docker runtime is also unusable: the bare Ubuntu image has no docker
# CLI needed by secret-scan, so local CI must always use emulation.

WORKFLOW = Config.ROOT_DIR / ".github" / "workflows" / "ci.yml"
GENERATED_WORKFLOW = Config.ROOT_DIR / ".wrkflw-local" / "ci.yml"


def _require_workflow() -> None:
    if not WORKFLOW.is_file():
        Printer.fail(f"workflow file not found: {WORKFLOW}")
        raise SystemExit(1)


def _require_wrkflw() -> None:
    if shutil.which("wrkflw") is None:
        Printer.fail("wrkflw not found on PATH; install wrkflw before running ci-local")
        raise SystemExit(127)


def list_jobs(workflow: Path) -> List[str]:
    """Return workflow job names in declaration order."""
    with workflow.open(encoding="utf-8") as stream:
        document = yaml.safe_load(stream) or {}
    jobs = document.get("jobs") or {}
    return list(jobs.keys())


def _patch_step(step: Any, default_working_directory: Any = None) -> Any:
    """Copy and patch one step without introducing shared YAML anchors."""
    if not isinstance(step, dict) or "run" not in step:
        return copy.deepcopy(step)

    patched = copy.deepcopy(step)
    working_directory = patched.get("working-directory") or default_working_directory
    target = '"$GITHUB_WORKSPACE"'
    if working_directory:
        target += f"/{working_directory}"
    patched["run"] = f"cd {target}\n{patched['run']}"
    return patched


def patch_workflow(source: Path, destination: Path) -> None:
    """Write a transformed copy whose run steps start in GITHUB_WORKSPACE.

    PyYAML follows YAML 1.1 and parses the bare key ``on`` as boolean ``True``.
    Restore it before dumping or wrkflw rejects the generated workflow because the
    required ``on`` property is missing. Every step is deep-copied so PyYAML emits
    no anchors or aliases when a source step is shared.
    """
    with source.open(encoding="utf-8") as stream:
        document = yaml.safe_load(stream) or {}

    if True in document:
        document["on"] = document.pop(True)

    jobs = document.get("jobs") or {}
    for job in jobs.values():
        if not isinstance(job, dict) or not isinstance(job.get("steps"), list):
            continue
        defaults = job.get("defaults") or {}
        run_defaults = defaults.get("run") or {}
        default_working_directory = run_defaults.get("working-directory")
        job["steps"] = [
            _patch_step(step, default_working_directory) for step in job["steps"]
        ]

    destination.parent.mkdir(parents=True, exist_ok=True)
    with destination.open("w", encoding="utf-8") as stream:
        yaml.safe_dump(
            document,
            stream,
            sort_keys=False,
            default_flow_style=False,
        )


class CiLocalCommand:
    """Run the repository CI workflow locally through wrkflw."""

    def run(self, args: argparse.Namespace) -> int:
        _require_workflow()

        if args.list:
            for job_name in list_jobs(WORKFLOW):
                print(job_name)
            return 0

        _require_wrkflw()

        if args.validate:
            return Runner.run(
                ["wrkflw", "validate", str(WORKFLOW)],
                check=False,
            ).returncode

        Printer.warn(
            "Run CI jobs serially: concurrent wrkflw runs share /tmp/.tmp* and "
            "clobber each other, causing phantom file-not-found errors."
        )
        try:
            patch_workflow(WORKFLOW, GENERATED_WORKFLOW)
            command = ["wrkflw", "run"]
            for job_name in args.jobs:
                command.extend(["--job", job_name])
            # Docker runtime has no docker CLI in wrkflw's container; host
            # emulation provides the CLI required by secret-scan.
            command.extend(["--runtime", "emulation", str(GENERATED_WORKFLOW)])
            return Runner.run(command, check=False).returncode
        finally:
            GENERATED_WORKFLOW.unlink(missing_ok=True)

    @staticmethod
    def register(parser: argparse.ArgumentParser) -> None:
        parser.add_argument(
            "--list",
            action="store_true",
            help="print workflow job names",
        )
        parser.add_argument(
            "--validate",
            action="store_true",
            help="validate the real GitHub Actions workflow",
        )
        parser.add_argument(
            "jobs",
            nargs="*",
            help="job names to run (default: all jobs)",
        )

