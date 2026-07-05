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
| T20 | Backlog | DSL auto-configuration | Spring bean reads dsl.source-dir, calls DefinitionLoader.load() at startup so Preview/Explain work out-of-the-box | High | TBD | T21 | - | `./docs/plans/T20-dsl-autoconfig.md` |
| T21 | Backlog | Temporal worker bootstrap | DslWorkerConfiguration creates Worker, registers generated ProcessDefinition/TransactionDefinition impls from classpath | High | TBD | - | T20 | `./docs/plans/T21-worker-bootstrap.md` |
| T22 | Backlog | GitHub Actions CI pipeline | .github/workflows/ci.yml running ./gradlew :dsl-codegen:build :dsl:build :starter:build on push/PR | Medium | TBD | - | - | `./docs/plans/T22-ci-pipeline.md` |
| T23 | Backlog | Starter integration test | @SpringBootTest covering full path: configure source-dir → load DSL → preview + explain via DslRuntimeResource | Medium | TBD | - | T20 | `./docs/plans/T23-starter-integration-test.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
