# Kanban Board

This file is a lightweight planner for tracking implementation tasks. It is intended to be used by the coding agent.

- **Use this file only as a high-level planner.** Detailed task descriptions, acceptance criteria, technical notes, and diagrams must be stored under [`./docs/plans`](./plans).
- The coding agent **must** update the `Status` column when taking a task and again when the work is finished.
- Keep the table sorted by `ID` for quick lookup.

## Status Legend

| Status        | Meaning                                                       | Who updates it  |
|---------------|---------------------------------------------------------------|-----------------|
| `Backlog`     | Task is defined but not yet ready to start.                   | Planner / Agent |
| `Ready`       | Task is ready to be picked up by the coding agent.            | Planner / Agent |
| `In Progress` | Coding agent is actively working on the task.                 | Coding agent    |
| `Review`      | Implementation done; pending review / validation.             | Coding agent    |
| `Blocked`     | Work cannot continue until another task or issue is resolved. | Coding agent    |
| `Done`        | Task completed and accepted.                                  | Coding agent    |

## Task Board

| ID   | Status  | Title                                                                                     | Description                                                                                                                                                                                                                                                                                                                                          | Priority | Owner | Blocks | Blocked By | Plan File                                                             |
|:-----|:--------|:------------------------------------------------------------------------------------------|:-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------:|:------|:-------|:-----------|:----------------------------------------------------------------------|
| T165 | Blocked | Preview execution sandboxing                                                              | backend — SecurityManager-based approach is infeasible: JDK 25 in use, and JEP 486 (JDK 24) permanently disabled SecurityManager enforcement — checkWrite/checkConnect/etc. are no longer invoked by JDK I/O paths at all. Needs human decision on an alternative isolation approach before any code is written. See plan file for options.          |   High   | loop  | -      | -          | `./docs/plans/T165-preview-execution-sandboxing.md`                   |
| T168 | Blocked | Preview/Explain runtime event-listener & report architecture                              | backend — Plan is stale: T162/T163/T164 already layered metrics/error-handling/caching directly onto `DevDslRuntime.preview()`'s current structure, and T173 (listed as blocked by this) shipped without it. Needs a replanning pass reconciling the SPI design with the new wrapping order before dispatch. See plan file.                          |   High   | loop  | T173   | -          | `./docs/plans/T168-preview-explain-listener-architecture.md`          |
| T222 | Blocked | DSL Workbench save/publish backend design                                                 | frontend/backend — `useDslWorkbench.ts` `saveConstruct()`/`publishConstruct()` are stubs (no backend endpoint); the DSL is compile-time-authored from Java source today, so "saving" an edit needs a product/architecture decision on persistence model and publish semantics before any plan or code is written. See plan file.                     |  Medium  | loop  | -      | -          | `./docs/plans/T222-workbench-save-publish-backend-design.md`          |
| T245 | Backlog | Fix stale `/api/dsl/helpers/search` doc references | frontend (docs) — `docs/architecture-ui.md` still describes the helper-search surface as `GET /api/dsl/helpers/search`; the route was renamed to `/api/dsl/objects/search` (backend fixed in T231, BFF proxy + frontend consumer already use the new path). Only the doc text lags. Docs-only change. See plan. | Low | loop | - | - | `./docs/plans/T245-fix-stale-helpers-search-doc-path.md` |
| T246 | Backlog | Component tests for remaining DSL workbench panels | frontend — T235 covered 4 of 10 `frontend/components/src/components/dsl/` components and explicitly flagged the other six (`BodyEditor`, `CodeTab`, `ConstructExplorer`, `HelperSearchPanel`, `InputMappingGrid`, `MetadataPanel`) as a follow-up; all six still have zero specs. Test-only change. See plan. | Medium | loop | - | - | `./docs/plans/T246-remaining-dsl-panel-component-tests.md` |
| T247 | Blocked | Resolve `SimpleExpressionEvaluator`'s deprecated-but-live-default status | backend — `SimpleExpressionEvaluator` carries `@Deprecated(forRemoval = true)` yet remains the wired default evaluator for platform-standalone runtime, and T241 just hardened 38 tests pinning its behavior. Needs a human decision: un-deprecate, replace with a real successor, or commit to a removal timeline. See plan. | Medium | loop | - | - | `./docs/plans/T247-simple-expression-evaluator-deprecation-decision.md` |
| T248 | Backlog | Unit test for `DslSyncedDirs` | backend — `backend/dsl-plugins/dsl-idea-plugin/.../DslSyncedDirs.java` (31 lines, tracks synced DSL/model dirs for the IntelliJ plugin) has no dedicated test while its sibling classes in the same package all do. Test-only change. See plan. | Low | loop | - | - | `./docs/plans/T248-dsl-synced-dirs-test.md` |
