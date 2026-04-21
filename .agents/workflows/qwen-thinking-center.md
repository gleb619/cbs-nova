---
description: Orchestration workflow that acts as a thinking center - analyzes requirements, creates task specs, delegates to Qwen via MCP tool, awaits results, and validates target files.
---

## Overview

This workflow orchestrates delegated implementation to Qwen through the `qwen_run` MCP tool. It follows a structured 5-phase process with retry logic and strict validation.

**CBS-Nova Project Context:**

- Stack: Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt 3 SPA · Tailwind CSS v4 · piqure DI · i18next · Biome · Gradle multi-module
- Modules: `backend` · `starter` · `client` · `frontend` · `frontend-plugin`
- Critical: Hexagonal architecture, frontend-plugin boundary, piqure DI patterns, JWT security (local RSA + Keycloak)

## Workflow

### Phase 1 — Analyze & Plan

- Read relevant codebase files to understand existing patterns and conventions
- Identify affected modules: `backend`, `starter`, `client`, `frontend`, `frontend-plugin`
- Choose a descriptive `kebab-case` task name (3-6 words, e.g., `add-auth-middleware`)
- Determine verification commands based on affected modules
- Identify files that MUST NOT be modified (`.agents/`, `.claude/`, `.kiro/`, `.qwen/`)

**Output:** Analysis summary with task name and scope confirmation.

### Phase 2 — Write Task Spec

Create the task specification file at `docs/tasks/{task-name}.md` with the following structure:

```markdown
# Task: {task-name}

## Description
<!-- What and why, one paragraph -->

## Context
<!-- Relevant patterns, constraints, reference files from docs/ -->

## Module Scope
- [ ] backend
- [ ] starter
- [ ] client
- [ ] frontend
- [ ] frontend-plugin

## Requirements

### Backend / Starter (if applicable)
1. ...

### Client (if applicable)
1. ...

### Frontend / Frontend-Plugin (if applicable)
1. ...

## Scope

### Files to Create
- `path/to/new/file`

### Files to Modify
- `path/to/existing/file` — reason

### Files to Leave Untouched
- `.agents/` — never modify
- `.claude/` — never modify
- `.kiro/` — never modify
- `.qwen/` — never modify
- `docs/` — reference only unless task explicitly targets docs

## Implementation Notes

### Backend / Starter
- Java 25 + Spring Boot 4.x
- Naming: `*Controller`, `*Service`, `*Repository`, `*Entity`, `*Mapper`, `*Dto`
- Layer rule: Controller → Service → Repository → Entity (no skipping)
- Use `@MockitoBean` (NOT `@MockBean`)
- Test methods: `shouldXxxWhenYyy` + `@DisplayName` required
- DB migrations: `starter/src/main/resources/db/migration/V{timestamp}__{description}.sql`
- Format before commit: `./gradlew spotlessApply`

### Frontend / Frontend-Plugin
- Hexagonal split: domain/ports in `frontend-plugin/`, adapters/pages in `frontend/`
- `frontend-plugin` must NEVER import from `frontend/`
- DI via piqure: `provide` and `inject` must come from the **same** `piqureWrapper` instance
- HTTP adapter uses `AxiosHttp` + Bearer token from `TokenStorage.get()`
- Biome 1.9.4: 2-space indent, single quotes, semicolons, line width 140
- New feature checklist (7 steps): domain type → port → provider → HTTP adapter → page → router → Nuxt plugin

## Verification

Run ALL applicable commands. Results must be included in the result file.

### Backend
```bash
./gradlew spotlessApply
./gradlew :backend:test
./gradlew :backend:integrationTest   # only if Docker is running
./gradlew check
```

### Starter
```bash
./gradlew :starter:test
./gradlew :starter:check
```

### Client (after OpenAPI changes)
```bash
./gradlew :backend:exportOpenApi
./gradlew generateAllClients
```

### Frontend
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend && pnpm lint
cd frontend && pnpm test
cd frontend && pnpm build
```

### Frontend-Plugin
```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
cd frontend-plugin && pnpm lint
cd frontend-plugin && pnpm test
```

### Expected Outcomes
- All commands exit code 0
- All "Files to Create" exist on disk
- No hardcoded secrets or credentials
- Naming conventions followed (ArchUnit will catch violations)
- `frontend-plugin` has no imports from `frontend/`

## Result File
After completing the task, write your result summary to:
`docs/results/{task-name}.result.md`
```

**Output:** Task spec written to `docs/tasks/{task-name}.md`.

### Phase 3 — Delegate via MCP

Invoke the `qwen_run` MCP tool:

**Tool:** `qwen_run`
**Parameters:**
```json
{
  "task_name": "{task-name}",
  "timeout_sec": 300,
  "args": ["-y", "--output-format", "text"]
}
```

The tool will:
1. Read `docs/tasks/{task-name}.md`
2. Spawn Qwen with the task instructions
3. Wait for Qwen to complete implementation
4. Qwen writes result to `docs/results/{task-name}.result.md`
5. Return the execution output

**Output:** Qwen execution completed (success or failure).

### Phase 4 — Validate Result

After the MCP tool returns:

1. **Check result file exists:** `docs/results/{task-name}.result.md`
2. **Parse the result file:**
   - Read the **Status** section (DONE | PARTIAL | FAILED)
   - Read the **Self-Assessment** section
   - Review **Files Changed** table
3. **Re-run verification commands independently** to confirm
4. **Check expected outcomes:**
   - All verification commands exit 0
   - All "Files to Create" exist on disk
   - No `frontend-plugin` → `frontend` imports
   - Naming conventions followed
5. **Make PASS/FAIL decision**

**Output:** Validation report with PASS/FAIL verdict.

### Phase 5 — Retry or Report

#### On PASS
- Report completion with summary
- Reference the result file location

#### On FAIL (1st attempt)
1. Append `## Retry Notes` section to `docs/tasks/{task-name}.md`:
   ```markdown
   ## Retry Notes (Attempt 2)
   
   The following issues were found in attempt 1:
   
   - [Specific issue 1]
   - [Specific issue 2]
   
   Please fix these specifically before re-running verification.
   ```
2. Re-invoke `qwen_run` with the same `task_name`
3. Proceed to Phase 4 again

#### On FAIL (2nd attempt - max retries reached)
1. **Do not attempt further retries**
2. Report to user with:
   - Summary of what was tried
   - Exact failure reason from result file
   - Suggestion for manual intervention

## Abort Conditions

STOP the workflow and escalate to the user if:

- Qwen fails twice (maximum 2 total runs, 1 retry allowed)
- Any CRITICAL security finding is detected in the result
- Frontend-plugin boundary violations are detected
- More than 3 consecutive verification failures occur
- The planner (if invoked) identifies a file marked "MUST NOT be modified" as required
- Hexagonal architecture layer violations occur
- Gradle module dependency conflicts arise

## Phase Status Updates

After each phase, provide a status update using this format:

```
## Phase X Complete — [Status]

Summary: [Brief description of what was accomplished]
Task file: docs/tasks/{task-name}.md
Result file: docs/results/{task-name}.result.md (if applicable)
Verdict: [PASS / NEEDS_RETRY / FAILED]
Next: [What happens next]
```

## Naming Conventions

| Artifact  | Path                                 |
|-----------|--------------------------------------|
| Task spec | `docs/tasks/{task-name}.md`          |
| Result    | `docs/results/{task-name}.result.md` |

`task-name` rules:
- `kebab-case`
- Descriptive but concise (3–6 words max)
- Examples: `add-auth-middleware`, `refactor-user-service`, `fix-pagination-bug`, `implement-dsl-error-handling`

## Usage Examples

> Use the qwen-thinking-center workflow to implement DSL compiler error handling
> Use the qwen-thinking-center workflow to add REST API for workflow versioning
> Use the qwen-thinking-center workflow to create UI components for mass operations
> Use the qwen-thinking-center workflow to refactor the token storage system
> Use the qwen-thinking-center workflow to add integration tests for DSL execution

## Quick Reference Checklist

**Before invoking MCP tool:**
- [ ] Analysis complete — all affected files/modules identified
- [ ] Task file written to `docs/tasks/{task-name}.md`
- [ ] Description is clear and unambiguous
- [ ] Requirements are numbered and concrete
- [ ] Module scope checkboxes marked
- [ ] Verification section has runnable commands + expected outcomes
- [ ] Result file path stated inside the task file

**After MCP tool returns:**
- [ ] Result file exists at `docs/results/{task-name}.result.md`
- [ ] Verification commands re-run independently
- [ ] All expected outcomes checked
- [ ] CBS-Nova conventions verified
- [ ] PASS/FAIL decision made before considering retry

**On retry:**
- [ ] Retry Notes section appended to task file
- [ ] Specific issues described clearly
- [ ] Re-invoke `qwen_run` with same task_name
