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
| T55 | Backlog | Frontend project scaffold | Create the pnpm workspace, Nuxt 3 admin UI, reusable Vue component library, and shared Tailwind preset from `docs/colors.md`. | High | loop | T56, T57, T58, T59, T60 | - | `./docs/plans/T55-frontend-project-scaffold.md` |
| T56 | Backlog | Admin UI layout shell | Implement the responsive admin shell with collapsible sidebar, sticky top app bar, mobile drawer, and scrollable main content area per `docs/frontend/layout.md`. | High | loop | T57, T58, T59 | T55 | `./docs/plans/T56-admin-ui-layout-shell.md` |
| T57 | Backlog | DSL Workbench UI | Build the construct explorer, split editor, metadata/body panels, inline validation, and save/publish flow per `docs/frontend/dsl-workbench.md`. | High | loop | - | T55, T56, T60 | `./docs/plans/T57-dsl-workbench-ui.md` |
| T58 | Backlog | Runner UI | Implement definition selector, Preview/Run/Explain mode switcher, auto-generated input form, output panel, and Mermaid diagram rendering per `docs/frontend/runner.md`. | High | loop | - | T55, T56, T60 | `./docs/plans/T58-runner-ui.md` |
| T59 | Backlog | Execution details UI | Build execution list, detail view, hierarchical trace, diagram/I/O/metadata/logs/errors tabs, and compensation visualization per `docs/frontend/execution-details.md`. | High | loop | - | T55, T56, T60 | `./docs/plans/T59-execution-details-ui.md` |
| T60 | Backlog | Admin UI BFF integration | Create Nitro server routes that proxy authenticated requests from the Nuxt admin UI to the Spring Boot backend and provide typed client composables. | High | loop | T57, T58, T59 | T55 | `./docs/plans/T60-admin-ui-bff-integration.md` |
| T61 | Backlog | Starter helper library | Create a set of simple reusable `@Helper` classes in `backend/starter` (formatMessage, sumValues, currentTimestamp, filterRecords, and a conditional exception helper) so DSL examples have building blocks and a deterministic exception path. | High | loop | T62, T63, T64 | T49 | `./docs/plans/T61-starter-helper-library.md` |
| T62 | Backlog | DSL simple examples | Add entry-level compact DSL files to `backend/dsl-examples/src/` demonstrating typed records, parameter-based definitions, and basic Helper/Function/Transaction calls. | High | loop | T63, T64 | T61 | `./docs/plans/T62-dsl-simple-examples.md` |
| T63 | Backlog | DSL intermediate examples | Add DSL files with loops, collection processing, multiple helper orchestration, and simulated long-running work/heartbeat to exercise non-trivial control flow. | High | loop | T64 | T61, T62 | `./docs/plans/T63-dsl-intermediate-examples.md` |
| T64 | Backlog | DSL advanced examples | Add DSL files that exercise compensation/rollback, multi-step sagas, and exception propagation using the conditional exception helper from T61. | High | loop | - | T61, T62, T63 | `./docs/plans/T64-dsl-advanced-examples.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
