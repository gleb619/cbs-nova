# Frontend Documentation

This documentation set describes the design of the cbs-nova admin UI, a Vue/Nuxt interface for authoring DSL flows, running them against the Temporal backend, and inspecting execution results.

## Purpose

The frontend gives operators and flow authors a single place to manage orchestration definitions end-to-end: write DSL sources, trigger runs or dry-runs, and dig into workflow executions without leaving the browser.

## Core user stories

1. **Edit DSL** — As a flow author, I want to open a DSL source in a structured editor so I can create or update Processes, Transactions, Functions, and Helpers safely.
2. **Run DSL** — As an operator, I want to start a workflow or preview run from the UI with visible inputs and options so I can validate behavior against the Temporal backend.
3. **Inspect execution** — As a support engineer, I want to open a completed or running execution to see its status, event history, inputs/outputs, and any errors so I can diagnose issues quickly.

## File map

| Document | Focus |
|----------|-------|
| [layout.md](./layout.md) | Overall page layout, navigation, and responsive shell |
| [dsl-workbench.md](./dsl-workbench.md) | DSL editing experience: editor, validation, and flow structure |
| [runner.md](./runner.md) | Run/preview controls, input forms, and mode selection |
| [execution-details.md](./execution-details.md) | Execution view, event history, status, and diagnostics |

## Relationship to other docs

- For the full technical architecture of the Vue/Nuxt app and its backend-for-frontend, see [`../architecture-ui.md`](../architecture-ui.md).
- For the Tailwind color palette and semantic color usage applied throughout these screens, see [`../colors.md`](../colors.md).
