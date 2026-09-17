---
name: cbs-nova-kanban
description: Manipulate the cbs-nova kanban board in docs/kanban.md. Use whenever asked to read, add, update, remove, or pick the next task from the kanban.
metadata:
  short-description: cbs-nova kanban board management
---

# cbs-nova Kanban Skill

Operate the Markdown kanban board at `docs/kanban.md`. The board tracks implementation tasks for the coding agent.

## When to use

- Reading tasks or current status.
- Picking the next executable task.
- Updating task status (`Backlog`, `In Progress`, `Blocked`, `Done`).
- Adding new tasks.
- Removing completed tasks.

## Tooling

Use the make-driven CLI. Do not edit `docs/kanban.md` by hand.

```bash
make kanban-list                          # all tasks
make kanban-next                          # next executable (priority, unblocked)
make kanban-start                         # mark next task In Progress
make kanban-status ID=<id> STATUS=<s>   # change status
make kanban-show ID=<id> [WITH_PLAN=1]    # show task details (with optional 100-line plan preview)
make kanban-add TITLE="..." [PRIORITY=... OWNER=... BLOCKS=... BLOCKED_BY=... PLAN=... DESCRIPTION=...]
make kanban-clean                         # remove Done rows
make kanban-test                          # sandbox regression test on kanban.test.md
```

Use `KANBAN_FILE=<path>` to target a different board.

## Showing task details

Use `make kanban-show ID=<id>` to render one task's fields in a readable, label-aligned block.
Use `make kanban-show ID=<id> WITH_PLAN=1` to also print the first 100 lines of the linked plan file; if the plan has more than 100 lines the preview ends with `...` and a note with the absolute plan path.

## Task selection rules

- If any task is `In Progress`, return it.
- Otherwise pick the highest-priority unblocked `Backlog` task, then lowest ID.
- A task is blocked if any `Blocked By` dependency is not `Done`.

## Adding tasks

When asked to add a task, prefer `make kanban-add`. Title required. Put `DESCRIPTION` last. `make kanban-add` is idempotent: if the same `ID` already exists, the command updates only the fields you provide and preserves existing values for omitted fields. Omit `ID` to auto-generate `T<max+1>`.

Default:
- `Status = Backlog` (preserved on update)
- `Priority = Low`
- `Owner = loop`
- `Blocks/Blocked By/Plan File = -`
- `Description = -` (max 128 characters; longer values are accepted, trimmed to 125, and suffixed with `...`; a warning is logged when trimming)
- `ID = T<max+1>`, inserted sorted.

## Status values

Only: `Backlog`, `In Progress`, `Blocked`, `Done`.

## Do not

- Edit the kanban table by hand.
- Remove `Blocked` tasks during cleanup — keep them for human review.
- Pick a `Backlog` task that is blocked.

## Definition of Done for kanban work

- Make command used produced expected output.
- `make kanban-test` passes if the CLI changed.
- Board left structurally valid (kanban lint not broken by this change).
