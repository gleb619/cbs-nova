# Kanban Board

This file is a lightweight planner for tracking implementation tasks. It is intended to be used by the coding agent.

- **Use this file only as a high-level planner.** Detailed task descriptions, acceptance criteria, technical notes, and diagrams must be stored under [`./docs/plans`](./plans).
- The coding agent **must** update the `Status` column when taking a task and again when the work is finished.
- Keep the table sorted by `ID` for quick lookup.

## Status Legend

| Status       | Meaning                                              | Who updates it     |
| ------------ | ---------------------------------------------------- | ------------------ |
| `Backlog`    | Task is defined but not yet ready to start.            | Planner / Agent    |
| `Ready`      | Task is ready to be picked up by the coding agent.    | Planner / Agent    |
| `In Progress`| Coding agent is actively working on the task.        | Coding agent       |
| `Review`     | Implementation done; pending review / validation.   | Coding agent       |
| `Blocked`    | Work cannot continue until another task or issue is resolved. | Coding agent |
| `Done`       | Task completed and accepted.                         | Coding agent       |

## Task Board

| ID | Status | Title | Description | Priority | Owner | Blocks | Blocked By | Plan File |
| :- | :----- | :---- | :---------- | :------: | :---- | :----- | :--------- | :-------- |
| T14 | Backlog | DslRuntime contract | ExplainReport record + DslRuntime interface in dsl-api; GlobalManager lookup extensions in dsl | High | TBD | T15,T16 | - | `./docs/plans/T14-dsl-runtime-contract.md` |
| T15 | Backlog | DevDslRuntime service | Spring @Service implementing DslRuntime via GlobalManager for preview/explain/run modes | High | TBD | T16 | T14 | `./docs/plans/T15-dev-dsl-runtime.md` |
| T16 | Backlog | REST runtime surface | DslRuntimeResource @RestController with /api/dsl/preview, /run, /explain endpoints | High | TBD | - | T15 | `./docs/plans/T16-rest-runtime.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
