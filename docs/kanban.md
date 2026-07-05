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
| T32 | Done | DSL reload endpoint | POST /api/dsl/reload resets GlobalManager and re-loads DSL from dsl.source-dir | Medium | loop | - | - | `./docs/plans/T32-reload-endpoint.md` |
| T33 | Done | Global exception handler | @RestControllerAdvice catching unhandled exceptions, returns ErrorResponse with 500 | Medium | loop | - | - | `./docs/plans/T33-exception-handler.md` |
| T34 | Done | RetryPolicy builder flow tests | Tests verifying retryPolicy chains through TransactionBuilder → DslObject → TransactionDescriptor | Low | loop | - | - | `./docs/plans/T34-retry-policy-tests.md` |
| T35 | Done | OpenAPI integration | springdoc-openapi-starter-webmvc-ui + @Operation annotations + /swagger-ui active | Low | loop | - | - | `./docs/plans/T35-openapi.md` |
| T36 | Done | Fix DslReloadResource | Remove Spring bean injection of GlobalManager, add reset before load, add try/catch for 500 | High | loop | - | - | `./docs/plans/T36-fix-reload-resource.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
