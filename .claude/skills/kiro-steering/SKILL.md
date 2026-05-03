---
name: kiro-steering
description: Generate or update Kiro steering files (product.md, structure.md, tech.md) by analyzing an existing codebase. Use this skill whenever the user mentions Kiro, steering files, .kiro/steering/, or asks to generate/update product.md, structure.md, or tech.md. Also trigger when the user says things like "document my project for the AI", "set up AI context files", or "help Kiro understand my codebase". Supports full generation of all three files or partial updates to individual files.
---

# Kiro Steering File Generator

Kiro steering files are persistent Markdown documents in `.kiro/steering/` that give the AI agent permanent context
about a project. They are read before every task — so they must be accurate, concise, and structured for LLM
consumption, not human browsing.

The three canonical files:

- **product.md** — *What* is being built: vision, core concepts, execution flow, current status
- **structure.md** — *Where* things live: folder layout, module responsibilities, naming rules, architecture patterns,
  feature checklists
- **tech.md** — *How* it's built: stack tables, build system, common commands, env vars, code style rules

## Workflow

### Step 1 — Determine scope

First, check what the user asked for:

- All three files → generate all
- A specific file name mentioned → generate only that one
- "Update" or "refresh" + a file → regenerate that file, preserve the others

If scope is unclear, ask: *"Should I generate all three steering files, or just one?"*

### Step 2 — Explore the codebase

Before writing anything, explore the repository to gather raw facts. Do this systematically:

```bash
# Root layout
find . -maxdepth 2 -not -path '*/node_modules/*' -not -path '*/.git/*' -not -path '*/build/*' -not -path '*/.gradle/*' | sort

# Package managers / build system
cat package.json 2>/dev/null || true
cat pnpm-workspace.yaml 2>/dev/null || true
cat build.gradle 2>/dev/null || true
cat settings.gradle 2>/dev/null || true
cat gradle/libs.versions.toml 2>/dev/null || true
cat Cargo.toml 2>/dev/null || true
cat pyproject.toml 2>/dev/null || true
cat go.mod 2>/dev/null || true

# Existing docs / README
cat README.md 2>/dev/null || true
ls docs/ 2>/dev/null || true

# Existing steering files (for partial updates)
cat .kiro/steering/product.md 2>/dev/null || true
cat .kiro/steering/structure.md 2>/dev/null || true
cat .kiro/steering/tech.md 2>/dev/null || true
```

For any modules that look significant, also explore one level deeper into `src/` or equivalent to understand layer
conventions, naming patterns, and test locations.

Collect:

- All languages and frameworks in use (with versions if visible)
- Module/package names and what each does
- Build and dev commands from scripts, Makefiles, Gradle tasks, etc.
- Test setup (unit vs integration, locations, runners)
- DB / migration tooling
- Auth approach
- Architectural patterns (hexagonal, layered, DDD, etc.)
- Environment variables with defaults
- Code style tooling and notable conventions

### Step 3 — Fill gaps with targeted questions

After exploring, identify what cannot be inferred from files. Ask only what is truly unknowable from the code — things
like:

- Project purpose / business domain (if there's no README)
- Current status (alpha, production, design phase)
- Non-obvious architectural decisions and why they were made
- Domain-specific concepts the AI would need to understand

Keep it to **one focused question block** — don't interrogate the user. If the codebase has a README or docs, extract
answers from there first.

### Step 4 — Write the files

Follow the format guide in `references/format-guide.md`. Write all requested files before presenting them.

Output the files to `.kiro/steering/` in the project root. Create the directory if it doesn't exist:

```bash
mkdir -p .kiro/steering
```

### Step 5 — Present and confirm

Show the user what was written. If anything was inferred rather than explicit in the code, call it out briefly: *"I
inferred X from Y — let me know if that's wrong."*

---

## Partial Update Rules

When regenerating a single file:

1. Re-explore only the parts of the codebase relevant to that file
2. Read the existing version of that file first
3. Preserve the overall structure and any sections that are still accurate
4. Only replace stale sections — don't rewrite things that haven't changed
5. Do not touch the other two steering files

---

## Quality Standards

A good steering file set has these properties:

**Completeness** — An AI agent with only these three files should be able to: add a new feature without asking where to
put it, choose the right tools and follow existing conventions, run the project locally, and understand what it's for.

**Conciseness** — No padding, no generic descriptions that could apply to any project. Every sentence earns its place.

**LLM-optimized structure** — Tables for stack/commands/env vars (scannable), code blocks for examples and patterns,
flat headers (H2/H3 only), explicit "critical" callouts for gotchas.

**Accuracy over completeness** — It is better to omit a section than to fill it with guesswork. If something is
uncertain, note it explicitly: *"(verify: may have changed)"*

---

## Reference Files

- `references/format-guide.md` — Detailed format spec and annotated examples for each of the three files. Read this
  before writing.