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

| ID   | Status      | Title                                                        | Description                                                                                                                                                                                                                                                                                         | Priority | Owner | Blocks                  | Blocked By    | Plan File                                                              |
|:-----|:------------|:-------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------:|:------|:------------------------|:--------------|:-----------------------------------------------------------------------|
| T165 | Blocked | Preview execution sandboxing | backend — SecurityManager-based approach is infeasible: JDK 25 in use, and JEP 486 (JDK 24) permanently disabled SecurityManager enforcement — checkWrite/checkConnect/etc. are no longer invoked by JDK I/O paths at all. Needs human decision on an alternative isolation approach before any code is written. See plan file for options. | High | loop | - | - | `./docs/plans/T165-preview-execution-sandboxing.md`                    |
| T168 | Blocked | Preview/Explain runtime event-listener & report architecture | backend — Plan is stale: T162/T163/T164 already layered metrics/error-handling/caching directly onto `DevDslRuntime.preview()`'s current structure, and T173 (listed as blocked by this) shipped without it. Needs a replanning pass reconciling the SPI design with the new wrapping order before dispatch. See plan file. | High | loop | T173 | - | `./docs/plans/T168-preview-explain-listener-architecture.md`           |
| T219 | Done | Remove `synchronized` from `InMemoryTransactionExecutionRepository` | backend — `save`/`deleteByRunId` are still marked `synchronized` even though the backing `ConcurrentHashMap`+`CopyOnWriteArrayList` are already thread-safe. Continue the T209/T212/T214/T215/T216 lock-free cleanup series. See plan. | High | loop | - | - | `./docs/plans/T219-in-memory-transaction-execution-repository-lock-free.md` |
| T220 | Backlog | Refresh architecture docs to reflect T205–T218 shipped work | docs — `docs/architecture-backend.md`/`architecture-ui.md` still describe the system as of the T203 refresh (through T201); 17 tasks shipped since (T205–T218): JDBC capture typed decorators, DefinitionLoader interface split, Jackson3 schema generator, TransactionExecutionRepository + JDBC/Flyway, TemporalDslService startup registration, lock-free cleanups, executions offset pagination + FE paging controls. Doc-accuracy only, no code. Tier backend (docs only). See plan. | High | loop | - | - | `./docs/plans/T220-refresh-architecture-docs-t205-t218.md` |
| T221 | Backlog | Remove `synchronized` fallback from `SingletonSupport.SingletonScope` | backend — `onLameWay` recovery path (triggered on `ConcurrentHashMap` recursive-update detection) still uses `synchronized (instances)` double-checked locking even though `instances` is already a `ConcurrentHashMap`; replace with atomic `putIfAbsent`, consistent with the T215 `GlobalManager` cleanup. Foundational (`DslConfig`/`Replaceable` machinery) — keep diff scoped to `onLameWay`, run full `:dsl:test`. See plan. | High | loop | - | - | `./docs/plans/T221-singleton-support-lock-free.md` |
