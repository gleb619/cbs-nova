---
name: opencode-delegation
description: Use when delegating implementation work to OpenCode as an executor agent — Claude plans and writes a task spec, OpenCode executes it, Claude verifies the result. Triggers when user asks to "use opencode", "delegate to opencode", "let opencode implement this", or when a task is well-scoped and can be handed off for execution.
---

# Skill: Orchestrated Task Delegation to OpenCode

## Overview

Claude acts as the **thinking center**: it plans, decomposes, writes task
specifications, delegates execution to OpenCode, then verifies results.
OpenCode acts as the **executor**: it reads a task file and performs the work.

---

## Roles

| Agent      | Role         | Responsibility                                        |
|------------|--------------|-------------------------------------------------------|
| `claude`   | Orchestrator | Plan, write task spec, invoke OpenCode, verify result |
| `opencode` | Executor     | Read task file, perform work, write result file       |

---

## Workflow

```
[User Request]
      │
      ▼
[1. Claude: Analyze & Plan]
      │
      ▼
[2. Claude: Write docs/tasks/{task-name}.md]
      │
      ▼
[3. Claude: Invoke OpenCode via MCP tool]
      │   opencode_run(task_name="{task-name}", timeout_sec=300,
      │                prompt="Read the file docs/tasks/{task-name}.md carefully...")
      │   → Instructs OpenCode to follow all instructions, writing result to docs/results/{task-name}.result.md
      ▼
[4. OpenCode: Execute task, write result file]
      │
      ▼
[5. Claude: Verify result file + run verification commands]
      │
      ├── PASS ──► Done ✅
      │
      └── FAIL ──► [6. Claude: Amend task file, re-invoke OpenCode (retry #1)]
                         │
                         ▼
                   [7. Claude: Verify again]
                         │
                         ├── PASS ──► Done ✅
                         └── FAIL ──► Escalate to user ❌ (max retries reached)
```

---

## Step 1 — Analyze & Plan

Before writing the task file, Claude must:

- Understand the full scope of the request
- Identify affected files, directories, dependencies
- Decide on the exact verification method (tests, lint, grep, manual check)
- Choose a `task-name` in `kebab-case` (e.g., `add-auth-middleware`)

---

## Step 2 — Task File Format

File path: `docs/tasks/{task-name}.md`

````markdown
# Task: {task-name}

## Description

<!-- One paragraph: what this task is about and why -->

## Context

<!-- Relevant background: existing files, architecture notes, constraints -->

## Requirements

<!-- Numbered, concrete, unambiguous list -->

1. ...
2. ...
3. ...

## Scope

### Files to Create

- `path/to/new-file.ts`

### Files to Modify

- `path/to/existing-file.ts` — reason

### Files to Leave Untouched

- `path/to/sensitive-file.ts`

## Implementation Notes

<!-- Optional hints, patterns to follow, libraries to use -->

## Verification

<!-- OpenCode must run these after finishing work.
     Results must be included in the result file. -->

### Commands to Run

```bash
# example — adjust per project
npm run test -- --testPathPattern="auth"
npm run lint
npm run build
```

### Expected Outcomes

- All listed commands exit with code 0
- File `path/to/new-file.ts` exists
- `SomeClass` is exported from `path/to/index.ts`
- No `console.log` left in production code

## Result File

After completing the task, write a result file to:
`docs/results/{task-name}.result.md`
````

---

## Step 3 — Invoking OpenCode

After writing the task file, Claude invokes the `opencode_run` MCP tool:

```
Tool: opencode_run
Arguments:
  task_name: "{task-name}"
  timeout_sec: 300
  prompt: "Read the file docs/tasks/{task-name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/{task-name}.result.md as instructed."
```

> **Note:** The `opencode_run` MCP tool handles nvm setup automatically, buffers output,
> and logs to `/tmp/logs/{task-name}.log`. A PID file is created at `/tmp/logs/{task-name}.pid`
> while running. The tool returns the full stdout of the OpenCode execution as its result.
> `--dangerously-skip-permissions` is passed automatically for autonomous operation.
> Pass `args: ["--continue"]` to continue OpenCode's last session (resume context).

---

## Step 4 — Result File Format (OpenCode must produce this)

File path: `docs/results/{task-name}.result.md`

```markdown
# Result: {task-name}

## Status

<!-- DONE | PARTIAL | FAILED -->

## Summary

<!-- What was done, briefly -->

## Files Changed

| File | Action |
|------|--------|
| `path/to/file.ts` | created / modified / deleted |

## Verification Output

### Command: `npm run test`

exit code: 0
```

<stdout here>
```

### Command: `npm run lint`

exit code: 0

```
<stdout here>
```

## Issues Encountered

<!-- Any blockers, assumptions made, deviations from spec -->

## Self-Assessment

<!-- PASS or FAIL, and why -->

```

---

## Step 5 — Verification by Claude

After OpenCode finishes, Claude must:

1. **Check the result file exists** at `docs/results/{task-name}.result.md`
2. **Read the Self-Assessment** section — note if OpenCode flagged issues
3. **Re-run verification commands independently** to confirm exit codes
4. **Check expected outcomes** from the task file one by one
5. **Make a final PASS/FAIL decision**

---

## Step 6 — Retry Logic

- Maximum **2 total runs** (1 retry allowed)
- Before retrying, Claude **must amend the task file**:
  - Add a `## Retry Notes` section at the bottom
  - Describe exactly what failed and what to do differently

```markdown
## Retry Notes (Attempt 2)
The following issues were found in attempt 1:
- Test `auth.test.ts` failed because `JwtService` was not injected
- Lint error in `middleware.ts` line 42: missing semicolon

Please fix these specifically before re-running verification.
```

- Then re-invoke:

```
Tool: opencode_run
Arguments:
  task_name: "{task-name}"
  timeout_sec: 300
  prompt: "Read the updated file docs/tasks/{task-name}.md carefully. Pay attention to the 'Retry Notes' section at the bottom. Fix the listed issues and update docs/results/{task-name}.result.md with a fresh result."
```

---

## Step 7 — Escalation

If the task still fails after retry #1, Claude must:

1. **Not attempt further retries**
2. Report to the user with:
    - Summary of what was tried
    - Exact failure reason
    - Suggestion for how to proceed manually

---

## Naming Conventions

| Artifact  | Path                                 |
|-----------|--------------------------------------|
| Task spec | `docs/tasks/{task-name}.md`          |
| Result    | `docs/results/{task-name}.result.md` |

`task-name` rules:

- `kebab-case`
- Descriptive but concise (3–6 words max)
- Examples: `add-auth-middleware`, `refactor-user-service`, `fix-pagination-bug`

---

## Quick Reference Checklist (Claude internal)

Before invoking OpenCode, verify:

- [ ] Task file written to correct path
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path is stated inside the task file

After OpenCode finishes:

- [ ] Result file exists
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] PASS/FAIL decision made before considering retry
