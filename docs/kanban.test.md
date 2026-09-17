# Kanban Test Board

Small sandbox board for testing the kanban CLI.

## Status Legend

| Status        | Meaning                                                       |
| :------------ | :------------------------------------------------------------ |
| `Backlog`     | Task is defined but not yet ready to start.                   |
| `In Progress` | Coding agent is actively working on the task.                 |
| `Blocked`     | Work cannot continue until another task or issue is resolved. |
| `Done`        | Task completed and accepted.                                  |

## Task Board

| ID | Status      | Title                            | Description     | Priority | Owner | Blocks | Blocked By | Plan File          |
| :- | :---------- | :------------------------------- | :-------------- | :------- | :---- | :----- | :--------- | :----------------- |
| T1 | Done        | Foundation                       | Base layer done | High     | loop  | -      | -          | ./docs/plans/T1.md |
| T2 | In Progress | Current task                     | Agent is here   | High     | loop  | T3     | -          | ./docs/plans/T2.md |
| T3 | Backlog     | Waiting task                     | Depends on T2   | Medium   | loop  | -      | T2         | ./docs/plans/T3.md |
| T4 | Backlog     | Free task                        | No blockers     | Low      | loop  | T5     | -          | ./docs/plans/T4.md |
| T5 | Backlog     | Chained task                     | Depends on T4   | High     | loop  | -      | T4         | ./docs/plans/T5.md |
