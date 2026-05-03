---
name: kiro-specs
description: >
  Generate Kiro-style spec files (requirements.md, design.md, tasks.md) for a software feature.
  Use this skill whenever the user wants to spec out a feature, create a Kiro spec, write requirements,
  plan implementation tasks, or produce a design document for a new capability on an existing codebase.
  Also trigger when the user says "spec this out", "create a spec for", "write requirements for",
  "generate tasks for", or any similar phrasing — even if they don't mention Kiro by name.
---

# Kiro Specs Skill

Generate the three Kiro spec files for a feature on an existing codebase:

1. `requirements.md` — user stories + EARS-style acceptance criteria
2. `design.md` — architecture, components, data models, properties, error handling, testing strategy
3. `tasks.md` — ordered implementation checklist with requirement traceability

## Workflow

### Step 1 — Understand the codebase context

Before writing anything, read enough of the codebase to understand:

- **Architecture pattern** (hexagonal, layered, MVC, etc.) and which layer owns what
- **Naming conventions** — how existing classes/files/components are named
- **Tech stack** — languages, frameworks, libraries in use
- **Testing approach** — what test libraries exist, where tests live, naming patterns
- **Analogous feature** — find a similar existing feature to mirror (this is the "north star" for structure)

Key files to look for:

- Root `README.md` or architecture docs
- An existing feature that resembles the new one (e.g. if adding `Settings`, find `Home` or another existing
  page/module)
- DI wiring files (`injections.ts`, `AppModule.java`, etc.)
- Router or route registration files
- Test directories and a sample test file

> **Important**: Mirror the patterns of the analogous feature exactly. Don't invent new conventions.

### Step 2 — Interview the user (if needed)

If the feature description is ambiguous, ask:

- What does this feature do from the user's perspective?
- Is there an existing feature to mirror structurally?
- Are there any known constraints (API shape, auth, existing data models)?
- Should property-based tests be included, or unit tests only?

Keep questions minimal — one round, max 3–4 questions.

### Step 3 — Generate all three files

Generate in order: `requirements.md` → `design.md` → `tasks.md`.
Each file builds on the previous. See detailed templates in `references/templates.md`.

Save all three files to `.kiro/specs/<feature-name>/` in the project (or current directory if project root is unclear).

### Step 4 — Present and confirm

Show the user all three files. Ask if anything needs adjustment before finalizing.

---

## File Conventions

### Requirement IDs

Number requirements as `N.M` where `N` is the requirement group number and `M` is the acceptance criterion index within
that group (e.g. `1.1`, `1.2`, `3.5`). Tasks reference these IDs.

### EARS Syntax for Acceptance Criteria

Use these trigger keywords consistently:

| Pattern                                              | When to use               |
|------------------------------------------------------|---------------------------|
| `THE <system> SHALL <behavior>`                      | Unconditional requirement |
| `WHEN <event>, THE <system> SHALL <behavior>`        | Event-triggered           |
| `IF <condition>, THEN THE <system> SHALL <behavior>` | Conditional               |
| `WHILE <state>, THE <system> SHALL <behavior>`       | State-dependent           |

Always capitalize the keyword (`THE`, `WHEN`, `IF`, `WHILE`, `THEN`).

### Task Checkbox States

- `- [ ] N.` — Not started
- `- [x] N.` — Completed
- `- [ ]* N.` — Optional (mark with `*`)

New specs should start all tasks as `- [ ]`.

### Task Granularity

Each task should be:

- A single coherent unit of work (one file, one class, one set of related changes)
- Followed by bullet points explaining exactly what to create/modify
- Ending with `_Requirements: X.Y, X.Z_` referencing the IDs it satisfies

Sub-tasks use decimal numbering: `5.1`, `5.2` under task `5`.

---

## Quality Checklist

Before delivering the files, verify:

**requirements.md**

- [ ] Glossary defines every domain term used
- [ ] Every user story follows "As a [role], I want [feature], so that [benefit]"
- [ ] Every acceptance criterion uses EARS syntax
- [ ] No implementation details leak into requirements (no file paths, no class names)

**design.md**

- [ ] Overview includes the full integration chain as a one-liner
- [ ] Mermaid architecture diagram is present
- [ ] Every component has an interface/signature shown
- [ ] Data model table maps TS types to backend DTO fields
- [ ] Correctness properties are defined (if property-based testing is used)
- [ ] Error handling table covers all failure scenarios
- [ ] Testing strategy section covers both unit and property tests

**tasks.md**

- [ ] Overview paragraph names the architecture pattern and which module owns what
- [ ] Tasks are ordered by dependency (domain types → ports → adapters → UI → routing → DI wiring)
- [ ] Every task references at least one requirement ID
- [ ] Checkpoint tasks (ensure all tests pass) are placed after logical groups
- [ ] Optional tasks are marked with `*`
- [ ] Notes section explains optional tasks and test placement

---

## Reference Files

- `references/templates.md` — Full annotated templates for all three files with inline guidance

Read `references/templates.md` before generating any file.