# T533 — pytest unit tests for `openapi/classify.py`

- **Tier:** backend (Python tooling)
- **Status:** Backlog (stub — refine at execution time)

## Goal

`scripts/src/cbs_cli/openapi/classify.py` (125 L) — the T419/T504 breaking-change classifier that
gates `make openapi-check` — has zero real pytest coverage. `openapi_diff_test.py` (despite the
`_test.py` filename that makes it look pytest-collectible) is actually a CLI self-test *command*
(`OpenApiDiffTestCommand`, invoked via `cbs_cli openapi-diff-test`) — its class isn't named `Test*`
and its method isn't `test_*`, so pytest does not collect or run it. The 3 fixture-based cases it
runs (additive-only, breaking+same-version, breaking+bumped-version) are useful black-box coverage
of the CLI exit-code path but exercise `classify()` only indirectly and don't isolate individual
diff rules.

## Current state

`scripts/test/test_lint_kanban.py` is the established real pytest pattern for this repo's Python
tooling: `sys.path.insert` to reach `src/`, direct function imports, plain `def test_*` functions,
no fixtures-as-files needed for pure-dict inputs. `classify.py` exposes:

- `load_spec(path)` — reads+parses JSON, `sys.exit(2)` on parse failure (side-effecting, test via
  a tmp file with invalid JSON, expect `SystemExit`).
- `classify(old, new) -> (breaking, additive)` — pure function over two OpenAPI-spec-shaped dicts;
  the real unit under test.
- Internals `_schema_props`, `_collect_schemas`, `_diff_schema` are private (leading underscore)
  but importable — test `classify()` behaviorally through its public surface; only reach for the
  private helpers if a rule is otherwise hard to isolate.

Rules to verify individually via minimal synthetic spec dicts (not full fixture files — smaller,
more targeted than the existing self-test fixtures):

- path added/removed → additive/breaking.
- operation (HTTP method) added/removed on a shared path → additive/breaking.
- request/response schema: property added/removed, required-property added/removed, property
  type changed, enum value added/removed → correct bucket each.
- schema removed entirely between old/new op → breaking.
- non-dict/malformed schema input (`_schema_props`'s defensive branch) → no crash, empty
  props/required, `enum=None`.

## Approach

1. New `scripts/test/test_classify.py`, same import-path idiom as `test_lint_kanban.py`.
2. Build minimal OpenAPI-shaped dict literals per case (skip full-document boilerplate — only the
   `paths`/`schema` shape `classify()` actually reads).
3. One test function per rule category above; assert exact message content only where stable,
   otherwise assert presence/absence + which list (breaking vs additive) it lands in.
4. `load_spec` — one test for successful parse, one for `SystemExit` on invalid JSON (use
   `pytest.raises(SystemExit)` + tmp_path fixture).

## Acceptance criteria

- [ ] New `scripts/test/test_classify.py`, collected and passing under the project's pytest
      invocation (check how `test_lint_kanban.py` is actually run in CI/Makefile — mirror it).
- [ ] Each diff rule has at least one isolated positive test.
- [ ] No changes to `classify.py`, `openapi_diff.py`, or `openapi_diff_test.py` (test-only,
      unless a genuine classifier bug is found — then flag Blocked with the finding).

## Files to create/modify (best guess)

- New: `scripts/test/test_classify.py`

## Build/test commands

```bash
cd scripts && python3 -m pytest test/test_classify.py -v
make lint  # if Python tests are wired into the aggregate gate; verify, don't assume
```

## Out of scope

- `openapi_diff_test.py`'s naming (it's a deliberate CLI self-test command, not a pytest bug to
  "fix" — do not rename its class to make pytest collect it, that would change its CLI behavior).
- `openapi_diff.py` CLI wiring itself, `lint_kanban.py`/other Python modules.
