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

| ID  | Status  | Title                                                        | Description                                                                                                                                                                                                       | Priority | Owner | Blocks | Blocked By | Plan File                                               |
|:----|:--------|:-------------------------------------------------------------|:------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|:--------:|:------|:-------|:-----------|:--------------------------------------------------------|
| T49 | Done    | misc-codegen @Helper SPI generator                           | Build annotation processor in `misc-codegen` that scans classes annotated with `@Helper` and generates SPI registration code so a `HelperResolver`/`HelperRegistry` can discover and register helpers at runtime. |   High   | loop  | -      | -          | `./docs/plans/T49-misc-codegen-helper-spi.md`           |
| T50 | Backlog | Bugsink integration                                          | Create docker-compose.yml at root and integrate Sentry/Bugsink SDK in starter module                                                                                                                              |  Medium  | loop  | -      | -          | `./docs/plans/T50-bugsink-integration.md`               |
| T51 | Backlog | Keycloak integration                                         | Add docker-compose.yml at root for Keycloak and integrate Spring Boot starter module with Keycloak (plan only, no implementation)                                                                                 |  Medium  | loop  | -      | -          | `./docs/plans/T51-keycloak-integration.md`              |
| T52 | Backlog | Full DSL Examples integration test with Temporal & Keycloak | Add `backend/integration-tests` module that compiles DSL from `dsl-examples`, starts Temporal and Keycloak in Testcontainers, and exercises generated DSL workflows via secured REST endpoints. | High | loop | - | T21, T51 | `./docs/plans/T52-dsl-examples-full-integration-test.md` |
| T53 | In Progress | Structured exception tracking with runId and exceptionId | Introduce runId on Context, exceptionId derived from runId in DslException root, stable DslErrorCode registry, and propagate through runtime/REST/logs. | High | loop | - | - | `./docs/plans/T53-structured-exception-tracking.md` |
| T54 | Backlog | Enrich dry-run/preview and explain responses | Extend preview and explain endpoints so they return structured reports with execution trace, external call details, call counts, and diagrams, enabling downstream report/diagram builders. | High | loop | - | - | `./docs/plans/T54-enrich-dry-run-preview-explain.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
