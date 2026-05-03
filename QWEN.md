# QWEN.md

This file provides guidance to Qwen Code when working with code in this repository.

## Project

CBS-Nova is a **business process orchestration engine** for core banking. See `AGENTS.md` for full architecture details.

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt.js BFF · Biome ·
Gradle

> This repo is currently in design phase. `docs/` contains TDDs; no implementation code exists yet.

---

## Development Workflow

This project has a 4-phase development workflow with specialized agents for planning, review, and testing.

### Available Resources

| Resource  | Path                                 | Purpose                                                                  |
|-----------|--------------------------------------|--------------------------------------------------------------------------|
| **Skill** | `.qwen/skills/dev-workflow/SKILL.md` | 4-phase orchestration: plan → implement → review → test                  |
| **Agent** | `.qwen/agents/planner.md`            | Senior architect — maps codebase, produces implementation plans          |
| **Agent** | `.qwen/agents/code-reviewer.md`      | Senior reviewer — 5-dimension review (security, quality, patterns, etc.) |
| **Agent** | `.qwen/agents/tester.md`             | QA engineer — writes tests, runs full suite, prevents regressions        |

### When to Use

- **New feature / major change**: "Use the dev-workflow skill to add user role management"
  → orchestrates planner → implement → code-reviewer → tester → commit
- **Planning only**: "planner agent, create a plan for adding audit logging"
  → produces `./plans/<feature>.md` without modifying code
- **Code review**: "code-reviewer agent, review these changed files"
  → evaluates regressions, security, quality, patterns, test coverage
- **Testing**: "tester agent, run full suite and add missing tests"
  → runs backend + frontend tests, writes new tests following project patterns

### Workflow Phases

1. **PLAN** — `planner` agent analyzes codebase, produces numbered plan saved to `./plans/`. User approval required.
2. **IMPLEMENT** — Execute plan step-by-step. Tests run after each step. Git checkpoint before medium/large steps.
3. **REVIEW** — `code-reviewer` evaluates changes across 5 dimensions. Verdict: PASS or NEEDS WORK.
4. **TEST & COMMIT** — `tester` runs full suite. Only if ALL GREEN: final commit with conventional message.

### Test Commands (used by workflow)

| Step | Command                                   |
|------|-------------------------------------------|
| 1    | `./gradlew :backend:test`                 |
| 2    | `./gradlew :backend:integrationTest`      |
| 3    | `cd frontend && pnpm test`                |
| 4    | `cd frontend && pnpm e2e` (if applicable) |

### Abort Conditions

The workflow stops and asks for guidance if:

- CRITICAL security finding detected
- More than 3 consecutive test failures on same step
- Planner requires a file marked "MUST NOT be modified"
- Frontend-plugin boundary or hexagonal architecture violations

---

## Browser Testing & Debugging

This project has Chrome DevTools MCP configured for browser-based debugging, performance analysis, and E2E testing.

### Available Resources

| Resource  | Path                                     | Purpose                                                     |
|-----------|------------------------------------------|-------------------------------------------------------------|
| **Skill** | `.qwen/skills/chrome-debugging/SKILL.md` | Chrome DevTools expertise, tool reference, usage guidelines |
| **Agent** | `.qwen/agents/browser-tester.md`         | Autonomous browser testing specialist                       |

### When to Use

- **Performance audit**: "Audit the admin UI load speed" → delegate to `browser-tester` agent
- **Debug frontend issues**: "Why is the Nuxt BFF showing a blank page?" → use `chrome-debugging` skill
- **E2E testing**: "Test the login → dashboard flow" → delegate to `browser-tester` agent
- **Lighthouse audit**: "Run accessibility check on the admin UI" → use `chrome-debugging` skill or `browser-tester`
  agent

### Quick Reference

The `browser-tester` agent should be auto-delegated for any task matching its description (browser automation, UI
testing, performance auditing). The `chrome-debugging` skill provides tool-level expertise when working directly with
Chrome DevTools MCP.

---

## Build Commands

### Backend (Java/Gradle)

```bash
./gradlew :backend:test          # Run tests
./gradlew :backend:checkstyleMain # Lint
./gradlew :backend:build -x test  # Build without tests
```

### Frontend (Vue/Nuxt, pnpm)

```bash
cd frontend && pnpm run lint      # Biome lint
cd frontend && pnpm run test:run  # Vitest tests
cd frontend && pnpm run build     # Production build
```

### Full Build

```bash
./gradlew build -x test
```

### Node.js Setup

```bash
source ~/.nvm/nvm.sh && nvm use v22.20.0
```
