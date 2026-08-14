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
| T201 | Done | Workbench dirty-state auto-save to localStorage | frontend — `useDslApi.saveDraft` is a stub (`return Promise.resolve({ success: true, name, payload })`), so users lose unsaved JSON/YAML work on refresh. The Workbench has rich input forms (BodyEditor, InputMappingGrid, MetadataPanel) but no client-side persistence. Goal: new `useWorkbenchDraft(name)` composable that auto-saves construct body to `localStorage` (key pattern `cbs.nova.draft.${name}`) on debounced change (250ms), with TTL (24h), single-tab scope, conflict-free upgrade path. On Workbench mount, restore from storage if newer than last saved-at; show "Restored from local draft" banner with discard action. Vitest spec locking save-restore-clear-TTL semantics. Tier frontend. See plan. | High | loop | - | - | `./docs/plans/T201-workbench-draft-autosave.md` |
