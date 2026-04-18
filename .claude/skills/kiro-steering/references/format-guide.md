# Kiro Steering File Format Guide

This guide defines the exact format, tone, and structure for each of the three Kiro steering files, with annotated
examples derived from real production files.

---

## General Principles (all files)

- **Title line**: `# <ProjectName> — <File Topic>` (e.g., `# CBS Nova — Tech Stack`)
- **Top-level sections**: H2 (`##`)
- **Sub-sections**: H3 (`###`) only when truly needed
- **Tables** for: stack layers, module responsibilities, commands, env vars, ports
- **Code blocks** for: CLI commands, file trees, architecture patterns, DI examples
- **No prose padding** — no sentences like "This project uses React, which is a popular JavaScript library"
- **No bullet soup** — prefer a table or a short labeled list over a wall of bullets
- **LLM-hostile traps to avoid**: vague verbs ("handles", "manages"), orphaned acronyms, implicit conventions

---

## product.md

### Purpose

Answers: *What is this system? What are its core concepts? How does it work at a high level?*

An AI agent reading product.md should understand the business domain, the key entities and their relationships, the main
execution flow, and where the project currently stands.

### Required sections

```markdown
# <ProjectName> — Product Overview

<2-3 sentence description: what it is, what problem it solves, what makes it distinct>

## Core Concepts

| Entity | Role |
|--------|------|
| ...    | ...  |

## Execution Flow

<Either a code block showing the request/data flow, or a numbered list of steps>

## Services

| Service | Port | Description |
|---------|------|-------------|
| ...     | ...  | ...         |

## Status

<One line: design/early implementation/production/etc. + any important context>
```

### Notes on each section

**Opening paragraph** — Be specific to this domain. "Business process orchestration engine for core banking" tells the
AI what kind of decisions to make. "A web application for managing data" tells it nothing.

**Core Concepts table** — List domain entities the AI will encounter in code and need to reason about. Keep Role
descriptions to one sentence. Include the relationships or lifecycle if non-obvious.

**Execution Flow** — Use a code block with `→` arrows for request flows. Show the actual path: endpoint → layer →
layer → storage. This is the most valuable thing for an AI generating new features.

**Services table** — List every running process with its port. Include the request chain if services proxy each other (
e.g., `Browser → Vite :9000 → Nuxt :3000 → Backend :7070`).

**Status** — Critical for calibrating how the AI responds. "Early implementation" means it should be willing to suggest
refactors. "Production" means it should be conservative.

### Example

```markdown
# CBS Nova — Product Overview

CBS Nova is a **business process orchestration engine** for core banking operations. It replaces Spring-bean
orchestration with a Temporal + PostgreSQL backend and a Kotlin Script DSL for business rules. Non-developers author
rules in `.kts` files; the engine compiles and executes them.

## Core Concepts

| Entity          | Role                                                                                                            |
|-----------------|-----------------------------------------------------------------------------------------------------------------|
| **Workflow**    | State machine backed by Temporal. All executions (even stateless) use a Temporal workflow.                      |
| **Event**       | Triggered operation inside a workflow state. Has `context{}`, `display{}`, `transactions{}`, `finish{}` blocks. |
| **Transaction** | Unit of work with optional `preview()` / `execute()` / `rollback()`. Rollback is a compensating entry.          |

## Execution Flow

\`\`\`
POST /api/events/execute
→ Spring: evaluate context{} (fails fast before Temporal)
→ Temporal workflow: state transition
→ Transaction chain: preview → execute (→ rollback on failure)
→ PostgreSQL: persist context, display_data, executed_transactions (JSONB, encrypted)
\`\`\`

## Services

| Service               | Port    | Description                                        |
|-----------------------|---------|----------------------------------------------------|
| Backend (Spring Boot) | `:7070` | Java API server + Temporal workflows               |
| Nuxt.js BFF           | `:3000` | Backend-for-Frontend, proxies `/api/**` to backend |

Request flow: `Browser → Vite (9000) → Nuxt BFF (3000) → Backend (7070)`

## Status

Project is in **design/early implementation phase**. `docs/` contains Technical Design Documents.
```

---

## structure.md

### Purpose

Answers: *Where does code live? What does each module own? What are the naming and layering rules? How do I add a new
feature?*

An AI agent reading structure.md should be able to place a new file without guessing, understand the architectural
boundaries, and follow the conventions already in place.

### Required sections

```markdown
# <ProjectName> — Project Structure

## Root Layout

\`\`\`
<project-root>/
├── module-a/ # one-line description
├── module-b/ # one-line description
...
\`\`\`

## Module Responsibilities

| Module | Type | Purpose |
|--------|------|---------|
| ...    | ...  | ...     |

## <Module> Structure

<Annotated file tree for each significant module>
<Naming conventions enforced by tooling (ArchUnit, linters, etc.)>
<Layer dependency rules>

## Test Structure

| Type | Location | Runner/Requires |
|------|----------|-----------------|
| ...  | ...      | ...             |

## <Key Architectural Pattern>

<Code example showing the pattern — DI, ports/adapters, etc.>

## Adding a New Feature (Checklist)

1. Step one → `path/to/file`
2. Step two → `path/to/file`
   ...
```

### Notes on each section

**Root Layout** — Always use a code block with inline comments. Every top-level directory should be explained. The tree
depth should be 1-2 levels max; deeper trees belong in per-module sections.

**Module Responsibilities table** — Include a "Type" column (Spring Boot app, Library JAR, Nuxt layer, etc.) — this
tells the AI whether the module is a runnable app or a dependency.

**Per-module file trees** — Annotate every non-obvious file. Show the naming pattern in action. If there's a layer
dependency rule (Controller → Service → Repository), state it explicitly.

**Key Architectural Pattern** — If the project uses a non-standard pattern (hexagonal, CQRS, etc.), include a minimal
code example showing *how* it works in this codebase specifically. Generic descriptions are useless; concrete examples
with actual type/function names are invaluable.

**Feature Checklist** — This is the highest-value section. A numbered list of exactly what to create and where when
adding a new feature. An AI that can follow this checklist will produce code consistent with the rest of the project
without being prompted.

### Critical rule for architecture boundaries

If there is a strict module boundary (e.g., `frontend-plugin/ must never import from frontend/`), state it explicitly as
a rule. The AI will respect it.

---

## tech.md

### Purpose

Answers: *What is the exact stack? How do I run things? What are the conventions for code style?*

An AI agent reading tech.md should be able to choose the right library/version, run the project from a cold start, and
write code that passes the linter on the first try.

### Required sections

```markdown
# Tech Stack — <ProjectName>

## Backend

| Layer | Technology |
|-------|------------|
| ...   | ...        |

## Frontend

| Layer | Technology |
|-------|------------|
| ...   | ...        |

## Build System

<Prose description of the build system topology — monorepo, workspaces, shared scripts>

## Common Commands

### Infrastructure

\`\`\`bash
...
\`\`\`

### Backend

\`\`\`bash
...
\`\`\`

### Frontend

\`\`\`bash
...
\`\`\`

### Code Generation (if applicable)

\`\`\`bash
...
\`\`\`

## Environment Variables

| Variable | Default | Effect |
|----------|---------|--------|
| ...      | ...     | ...    |

## Code Style Rules

### <Language/Runtime>

<Bullet list of non-obvious, project-specific rules — not generic advice>
```

### Notes on each section

**Stack tables** — Include the version for every dependency where it matters (frameworks, not transitive deps). Rows
should be: Language, Framework, key Persistence/Auth/Mapping/Testing libs. Don't list every dependency — only the ones
that affect how new code is written.

**Build System** — Explain the topology (monorepo? workspaces? shared scripts?). Name the shared config files and what
they do. An AI that doesn't understand the build system will suggest wrong commands.

**Common Commands** — Group by concern. Include the comment `# What this does` for non-obvious commands. Include
single-test and single-module variants — these are used constantly during development.

**Code Generation** — If OpenAPI clients, GraphQL types, or similar artifacts are generated from a source, include the
commands here. An AI should know not to hand-edit generated files.

**Environment Variables** — Include the default value (so the AI knows what works locally) and the effect (so it knows
when changing it matters).

**Code Style Rules** — Only include rules that are non-obvious or project-specific. Good rules to include:

- Deprecated annotations to avoid (e.g., `@MockBean` → use `@MockitoBean`)
- Naming conventions for test methods
- Required annotations (e.g., every test needs `@DisplayName`)
- Formatter settings that affect generated code (indent, quotes, line width)
- Things that are intentionally off (e.g., `noExplicitAny: off`)

---

## Anti-patterns to avoid in all three files

| Anti-pattern                        | Why bad                       | Fix                                       |
|-------------------------------------|-------------------------------|-------------------------------------------|
| "The backend handles requests"      | Zero information              | Describe the actual layer chain           |
| Generic stack list without versions | AI may use wrong APIs         | Always include versions                   |
| File tree without annotations       | AI doesn't know what files do | Annotate everything non-obvious           |
| "Follow best practices"             | Meaningless to an AI          | State the specific rule                   |
| Listing every dependency            | Noise overwhelms signal       | Only libs that affect authoring decisions |
| Stale commands                      | AI runs broken commands       | Note when commands need Docker/infra      |