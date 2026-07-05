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
| T1 | Ready | dsl-api core contracts | DslObject, Executable<IN,OUT>, Context<T>, Result<T>, @Helper annotation, ExecutionMode enum in package cbs.nova.dsl | High | TBD | T2 | - | `./docs/plans/T1-dsl-api-contracts.md` |
| T2 | Backlog | DSL builder API | Dsl static facade + ProcessBuilder, TransactionBuilder, FunctionBuilder with fluent API in cbs.nova.dsl | High | TBD | T3 | T1 | `./docs/plans/T2-dsl-builder-api.md` |
| T3 | Backlog | Registry layer | ProcessRegistry, TransactionRegistry, HelperRegistry — store DslObject definitions by name | High | TBD | T4 | T2 | `./docs/plans/T3-registry-layer.md` |
| T4 | Backlog | Runner layer | ProcessRunner, TransactionRunner, HelperRunner — execute DslObject against typed Context | High | TBD | T5 | T3 | `./docs/plans/T4-runner-layer.md` |
| T5 | Backlog | Manager layer & GlobalManager | ProcessManager, TransactionManager, HelperManager + GlobalManager singleton facade | High | TBD | - | T4 | `./docs/plans/T5-manager-layer.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
