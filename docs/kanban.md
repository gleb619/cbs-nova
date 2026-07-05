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
| T24 | In Progress | MermaidDiagramGenerator | Real Mermaid flowchart from descriptor in Explain mode replacing placeholder | Medium | TBD | - | - | `./docs/plans/T24-mermaid-generator.md` |
| T25 | Backlog | DSL introspection endpoints | GET /api/dsl/processes, /transactions, /helpers listing registered entity names | Medium | TBD | - | - | `./docs/plans/T25-introspection-endpoints.md` |
| T26 | Backlog | Spring PropertyResolver wiring | Auto-configure PropertyResolver bean from Spring Environment in starter | Low | TBD | - | - | `./docs/plans/T26-property-resolver.md` |
| T27 | In Progress | REST error response standardization | Structured ErrorResponse JSON record on preview/run failure instead of bare string | Low | TBD | - | - | `./docs/plans/T27-error-response.md` |

> **How to use:** Replace the example rows with real tasks. Create a matching plan file under `./docs/plans/<ID>-short-title.md` for each task that needs detailed instructions.
