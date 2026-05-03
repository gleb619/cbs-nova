---
name: qwen-delegation
description: Use when delegating implementation work to Qwen as an executor agent — Claude plans and writes a task spec, Qwen executes it, Claude verifies the result. Triggers when user asks to "use qwen", "delegate to qwen", "let qwen implement this", or when a task is well-scoped and can be handed off for execution.
---

# Skill: Orchestrated Task Delegation to Qwen

## Overview

Claude acts as the **thinking center**: it plans, decomposes, writes task
specifications, delegates execution to Qwen, then verifies results.
Qwen acts as the **executor**: it reads a task file and performs the work.

---

## Roles

| Agent    | Role         | Responsibility                                    |
|----------|--------------|---------------------------------------------------|
| `claude` | Orchestrator | Plan, write task spec, invoke Qwen, verify result |
| `qwen`   | Executor     | Read task file, perform work, write result file   |

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
[3. Claude: Invoke Qwen via CLI]
      │   source ~/.nvm/nvm.sh && nvm use v22.20.0 && \
      │   qwen -y "Read docs/tasks/{task-name}.md and follow all instructions
      │            inside. Write your result to docs/results/{task-name}.result.md"
      │            --output-format text
      ▼
[4. Qwen: Execute task, write result file]
      │
      ▼
[5. Claude: Verify result file + run verification commands]
      │
      ├── PASS ──► Done ✅
      │
      └── FAIL ──► [6. Claude: Amend task file, re-invoke Qwen (retry #1)]
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
<!-- Qwen must run these after finishing work.
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

## Step 3 — Invoking Qwen

After writing the task file, Claude runs:

```bash
qwen-run {task-name} -y "Read the file docs/tasks/{task-name}.md carefully and follow all instructions inside it exactly. After completing all work, write your result summary to docs/results/{task-name}.result.md as instructed." --output-format text
```

> **Note:** `-y` enables yolo mode (auto-approves file edits — required for unattended execution).
> `--output-format text` produces clean output for Claude to parse.
> **`qwen-run`** is a wrapper at `~/.local/bin/qwen-run` that handles nvm setup automatically.
> It supports an optional `--timeout <sec>` argument before `{task-name}` (default: 300s).
> Output is streamed directly to stdout, and also logged to `/tmp/logs/{task-name}.log`.
> If you need to verify it's still running in the background, check if `/tmp/logs/{task-name}.pid` exists.

---

## Step 4 — Result File Format (Qwen must produce this)

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

After Qwen finishes, Claude must:

1. **Check the result file exists** at `docs/results/{task-name}.result.md`
2. **Read the Self-Assessment** section — note if Qwen flagged issues
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

```bash
qwen-run {task-name} -y "Read the updated file docs/tasks/{task-name}.md carefully. Pay attention to the 'Retry Notes' section at the bottom. Fix the listed issues and update docs/results/{task-name}.result.md with a fresh result." --output-format text
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

Before invoking Qwen, verify:

- [ ] Task file written to correct path
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path is stated inside the task file

After Qwen finishes:

- [ ] Result file exists
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] PASS/FAIL decision made before considering retry
