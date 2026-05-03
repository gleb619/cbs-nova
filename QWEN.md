# QWEN.md

This file provides guidance to Qwen Code when working with code in this repository.

## Project

CBS-Nova is a **business process orchestration engine** for core banking. See `AGENTS.md` for full architecture details.

**Stack:** Java 25 · Spring Boot · Temporal · PostgreSQL · Kotlin Script (.kts) · Vue 3 admin UI · Nuxt.js BFF · Biome ·
Gradle

> This repo is currently in design phase. `docs/` contains TDDs; no implementation code exists yet.

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
