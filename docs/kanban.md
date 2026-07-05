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
| T6 | Backlog | DefinitionLoader | Scan JEP-512 compact source files, invoke define(), collect List<DslObject> into registries | High | TBD | T7 | - | `./docs/plans/T6-definition-loader.md` |
| T7 | Backlog | DSL descriptors | ProcessDescriptor, TransactionDescriptor, FunctionDescriptor — AST value objects extracted from DslObjects | High | TBD | T8 | T6 | `./docs/plans/T7-dsl-descriptors.md` |
| T8 | Backlog | Semantic validator | Validate parameter presence, helper ref resolution, cycle detection across descriptors | High | TBD | T9 | T7 | `./docs/plans/T8-semantic-validator.md` |
| T9 | Backlog | Property placeholder support | ${key} resolution in string fields of descriptors via configurable PropertyResolver | Medium | TBD | - | T8 | `./docs/plans/T9-property-placeholder.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
