import sys
from pathlib import Path

import yaml

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "src"))

from cbs_cli.commands.ci_local import patch_workflow  # noqa: E402

WORKFLOW = """\
name: CI
on: push
shared-step: &shared
  run: python3 -m pytest scripts/test -q
jobs:
  root-step:
    runs-on: ubuntu-latest
    steps:
      - *shared
  directory-step:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - run: pnpm test
        working-directory: frontend
      - *shared
  default-step:
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: scripts
    steps:
      - run: python3 -m pytest scripts/test -q
"""


def write_workflow(tmp_path: Path) -> Path:
    workflow = tmp_path / "ci.yml"
    workflow.write_text(WORKFLOW, encoding="utf-8")
    return workflow


def patch_inline_workflow(tmp_path: Path) -> dict:
    source = write_workflow(tmp_path)
    destination = tmp_path / "generated" / "ci.yml"
    patch_workflow(source, destination)
    with destination.open(encoding="utf-8") as stream:
        return yaml.safe_load(stream)


def test_patch_workflow_prefixes_every_run_step(tmp_path):
    workflow = patch_inline_workflow(tmp_path)
    steps = workflow["jobs"]["root-step"]["steps"]
    assert steps[0]["run"] == (
        'cd "$GITHUB_WORKSPACE"\npython3 -m pytest scripts/test -q'
    )

    directory_step = workflow["jobs"]["directory-step"]["steps"][1]
    assert directory_step["run"] == 'cd "$GITHUB_WORKSPACE"/frontend\npnpm test'
    assert directory_step["working-directory"] == "frontend"


def test_patch_workflow_honors_job_default_working_directory(tmp_path):
    workflow = patch_inline_workflow(tmp_path)
    default_step = workflow["jobs"]["default-step"]["steps"][0]
    assert default_step["run"] == (
        'cd "$GITHUB_WORKSPACE"/scripts\npython3 -m pytest scripts/test -q'
    )


def test_patch_workflow_restores_on_key(tmp_path):
    workflow = patch_inline_workflow(tmp_path)
    assert "on" in workflow
    assert True not in workflow


def test_patch_workflow_leaves_uses_steps_untouched(tmp_path):
    workflow = patch_inline_workflow(tmp_path)
    assert workflow["jobs"]["directory-step"]["steps"][0] == {
        "uses": "actions/checkout@v4"
    }


def test_patch_workflow_copies_shared_steps_without_yaml_aliases(tmp_path):
    source = write_workflow(tmp_path)
    destination = tmp_path / "generated.yml"
    patch_workflow(source, destination)
    generated_text = destination.read_text(encoding="utf-8")

    assert "&id" not in generated_text
    assert "*id" not in generated_text
    generated = yaml.safe_load(generated_text)
    root_step = generated["jobs"]["root-step"]["steps"][0]
    directory_step = generated["jobs"]["directory-step"]["steps"][2]
    assert root_step is not directory_step
    assert root_step == directory_step

