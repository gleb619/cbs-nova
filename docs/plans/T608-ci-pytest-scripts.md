# T608 — CI: run `scripts/test/` pytest suite

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

`.github/workflows/ci.yml` covers kanban lint, backend tests (platform/starter/plugins), full FE
pipeline, `make lint` — but never runs the python CLI's own tests
(`scripts/test/test_classify.py`, `test_lint_kanban.py`, plus `openapi_diff_test.py` inside
`commands/`; more coming via T607's `test_doctor.py`). The CLI gates repo hygiene
(lint-kanban runs in CI) yet its own logic is untested there.

Add a small CI job (or extend the lint job) that installs pytest and runs the suite:

```yaml
- run: pip install pytest
- run: python3 -m pytest scripts/test/
```

Note: `openapi_diff_test.py` sits inside `scripts/src/cbs_cli/commands/` — pytest default
discovery may not pick it up from the `scripts/test/` root. Either add a step running
`python3 -m pytest scripts/` with an `__init__.py`-free discovery config, or move/copy the test
into `scripts/test/` (moving preferred — one test location; keep imports working via the
existing `sys.path` bootstrap used by `test_classify.py`).

## Tier

`backend` (CI + python scripts)

## Acceptance criteria

- [ ] CI runs the full python test suite; step visible green on the next CI run.
- [ ] All existing python tests discovered (incl. `openapi_diff_test.py` — moved or configured).
- [ ] New job does not need Docker/network beyond pip (tests are pure-unit; verify none hit
      live services — if one does, skip-mark it rather than deleting).
- [ ] `python3 -m pytest scripts/test/` passes locally.
- [ ] `make lint` green.

## Out of scope

- Coverage reporting/upload.
- Any CLI behavior change.

## Files to create/modify

- `.github/workflows/ci.yml` (modify)
- `scripts/test/test_openapi_diff.py` (create — if moving `openapi_diff_test.py`)
- `scripts/src/cbs_cli/commands/openapi_diff_test.py` (delete — if moved)

## Build/test commands

```bash
pip install pytest
python3 -m pytest scripts/test/
make lint
```
