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

| ID   | Status      | Title                                                        | Description                                                                                                                                                                                                                                                                                         | Priority | Owner | Blocks                  | Blocked By    | Plan File                                                       |
|:-----|:------------|:-------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------:|:------|:------------------------|:--------------|:----------------------------------------------------------------|
| T165 | Blocked | Preview execution sandboxing | backend — SecurityManager-based approach is infeasible: JDK 25 in use, and JEP 486 (JDK 24) permanently disabled SecurityManager enforcement — checkWrite/checkConnect/etc. are no longer invoked by JDK I/O paths at all. Needs human decision on an alternative isolation approach before any code is written. See plan file for options. | High | loop | - | - | `./docs/plans/T165-preview-execution-sandboxing.md` |
| T168 | Blocked | Preview/Explain runtime event-listener & report architecture | backend — Plan is stale: T162/T163/T164 already layered metrics/error-handling/caching directly onto `DevDslRuntime.preview()`'s current structure, and T173 (listed as blocked by this) shipped without it. Needs a replanning pass reconciling the SPI design with the new wrapping order before dispatch. See plan file. | High | loop | T173 | - | `./docs/plans/T168-preview-explain-listener-architecture.md` |
| T202 | Done | Remove dead WhatIfConfigPanel (mocks removed by T198) | frontend — `WhatIfConfigPanel.vue` on `runner.vue` still builds a per-request `type:target:operation` mocks map, but T198 removed per-request mocking entirely (`DslRequest` has no `mocks` field anymore; faking is now startup-YAML-only via `cbs.nova.fakes`). The panel is dead — configuring a mock there silently does nothing. Remove the panel/tab, stop sending `mocks` from any composable, point authors at the YAML fake config instead. See plan. | High | loop | - | - | `./docs/plans/T202-remove-dead-whatif-mock-panel.md` |
| T203 | Backlog | Refresh architecture docs for T184-T201 | docs — `architecture-backend.md`/`architecture-ui.md` still reflect the T183 (`T146`-`T182`) state. Several statements are now factually wrong: mock injection section (superseded by T198 fake/HelperInterceptor mechanism), dry-run logging section (still describes deleted `ScopedValueDryRunLoggingContext`, superseded by T197's pipe-stage buffer). Roadmap table stops at T182. No mention of Executions page going live (T186/T199/T200) or Workbench draft autosave (T201). Correct stale claims + append roadmap rows, doc-only. See plan. | High | loop | - | - | `./docs/plans/T203-refresh-architecture-docs-t184-t201.md` |
| T204 | Backlog | Execution detail endpoint — surface input/output/errors | backend — T200's `GET /api/executions/{id}` only maps `id/entity/entityType/mode/status/startedAt/completedAt/duration`; FE `ExecutionDetail` type also has `input`/`output`/`errors`/`trace`/`logs`/`mermaidDiagram`. `DslRun` already stores `input`/`output`/`error` — just not surfaced. Add those three (DTO-only, no new capture). Explicitly NOT in scope: `trace`/`logs`/`mermaidDiagram` aren't captured for RUN-mode executions today (bigger follow-up task, don't fabricate). See plan. | High | loop | - | - | `./docs/plans/T204-execution-detail-enrichment.md` |
