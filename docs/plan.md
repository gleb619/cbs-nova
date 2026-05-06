# CBS-Nova — Implementation Plan

> Single source of truth for the CBS-Nova orchestration engine build-out.
>
> **Workflow:** For each task, create a spec under `docs/tasks/{id}-{slug}.md`
> from [task-template.md](task-template.md), delegate (see `/executor-delegation`),
> save result to `docs/results/{id}-{slug}.result.md` from [result-template.md](result-template.md),
> then mark task `done` here.

## Status Legend

| Symbol        | Meaning                                           |
|---------------|---------------------------------------------------|
| `todo`        | Not started                                       |
| `in-progress` | Actively being worked on                          |
| `done`        | Completed and verified                            |
| `blocked`     | Waiting on another task                           |
| `parked`      | Locked task, excluded from planning and execution |

---

## Active Task Summary

| ID  | Title                                                            | Status      | Module     |
|-----|------------------------------------------------------------------|-------------|------------|
| T81 | -                       | `todo`      | dsl        |

---

## Phase Summary

| Phase    | Status      | Tasks            | Documentation                                                             |
|----------|-------------|------------------|---------------------------------------------------------------------------|
| 1      | `done`      | T01–T24          | [phase1.md](papers/phase1.md)                                             |
| 2      | `done`      | T25–T40          | [phase2.md](papers/phase2.md)                                             |
| 3      | `done`      | T41–T59          | [phase3.md](papers/phase3.md)    |
| 4      | `done`      | T60–T80          | Java DSL pivot, prototype, codegen    |
| 5      | `todo`      | T81–?          | Java DSL works    |
