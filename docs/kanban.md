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
| T28 | In Progress | Execution trace in ExplainReport | Populate executionTrace list with step entries during explain dispatch | Medium | TBD | - | - | `./docs/plans/T28-execution-trace.md` |
| T29 | In Progress | TASK_QUEUE constant in generated classes | Emit private static final TASK_QUEUE from descriptor.taskQueue() in ProcessDefinition and TransactionDefinition | Medium | TBD | - | - | `./docs/plans/T29-taskqueue-constant.md` |
| T30 | In Progress | DSL HealthIndicator | Spring Boot HealthIndicator reporting count of loaded processes/transactions/helpers | Low | TBD | - | - | `./docs/plans/T30-health-indicator.md` |
| T31 | In Progress | Entity detail endpoints | GET /api/dsl/processes/{name} and /transactions/{name} returning version/taskQueue/types/hasCompensation | Medium | TBD | - | - | `./docs/plans/T31-entity-detail.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
