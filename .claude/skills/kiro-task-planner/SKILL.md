---
name: kiro-task-planner
description: Use when the user wants to plan, spec, or scaffold a new feature or task for CBS-Nova. Invokes the kiro MCP tool to scan the codebase and generate a structured task spec at docs/tasks/{T##-slug}.md using the project's canonical task template.
---

# Skill: Kiro Task Planner

## Overview

Claude invokes the `kiro_run` MCP tool to scan the CBS-Nova codebase and produce a
fully-populated task spec file following the project's canonical template.
The result is saved to `docs/tasks/{T##-slug}.md` and is ready to hand off
to User (via `executor-delegation` Claude skill) for implementation.

---

## When to Use

- User says "plan feature X", "create a task for X", "spec out X", "scaffold X"
- User wants a task file that follows the CBS-Nova task template format
- User wants to use kiro to generate a codebase-aware task spec

---

## Step 1 — Determine Task ID and Slug

1. List existing files in `docs/tasks/` and find the highest `T##` prefix.
2. Increment by 1 to get the next task ID (e.g. `T09` if highest is `T08`).
3. Slugify the feature name: lowercase, hyphens, no spaces (e.g. `temporal-config`).
4. Target file path: `docs/tasks/{T##}-{slug}.md` (e.g. `docs/tasks/T09-temporal-config.md`)

If `docs/tasks/` is empty or has no `T##` files, start at `T01`.

---

## Step 2 — Check for Conflicts

If the target file already exists, ask the user:
> "File `docs/tasks/{T##-slug}.md` already exists. Overwrite, append, or create `{T##-slug}-v2.md`?"

Do not proceed until the user confirms.

---

## Step 3 — Invoke Kiro CLI via MCP Tool

Invoke the `kiro_run` MCP tool:

```
Tool: kiro_run
Arguments:
  args: [
    "chat",
    "--no-interactive",
    "--trust-all-tools",
    "<full prompt as a single string>"
  ]
  task_id: "{T##-slug}"
```

The prompt string should instruct Kiro to:

1. Scan the entire project codebase before writing.
2. Use real file paths — do not hallucinate paths that do not exist. Mark files that will be created as `[NEW]`.
3. Read the existing task template at `docs/task-template.md`.
4. Read 2–3 existing task files in `docs/tasks/` to learn the format and naming conventions.
5. Write the spec for the requested feature following the canonical task template structure.

> The `kiro_run` MCP tool spawns `~/.local/bin/kiro-cli` directly and streams output.
> It returns the full stdout/stderr of the Kiro execution as its result.
> If `~/.local/bin/kiro-cli` is not found, the tool will return an error.

---

## Step 4 — Verify Output

After Claude writes the file:

- [ ] File exists at `docs/tasks/{T##-slug}.md`
- [ ] All listed file paths exist in the repo OR are explicitly marked `[NEW]`
- [ ] Task ID in the Identity table matches the filename prefix
- [ ] Acceptance criteria are testable, not vague
- [ ] No unfilled placeholder text remains (`[TBD]`, `...`, empty table rows)

If any check fails:

| Problem                   | Action                                                                    |
|---------------------------|---------------------------------------------------------------------------|
| Hallucinated paths        | Note the bad paths, re-invoke kiro with those paths called out explicitly |
| Missing sections          | Manually edit the file using `docs/task-template.md` as reference         |
| Kiro returns empty output | Re-invoke with `--verbose` flag and report output to user                 |

---

## Step 5 — Announce Result

Tell the user:
> "Task spec created at `docs/tasks/{T##-slug}.md`. Ready to delegate to User with the `executor-delegation` skill."

---

## Error Handling

| Condition                         | Action                                                                          |
|-----------------------------------|---------------------------------------------------------------------------------|
| `~/.local/bin/kiro-cli` not found | Tell user: check that kiro-cli is installed at `~/.local/bin/kiro-cli`          |
| File already exists               | Ask user: overwrite / append / versioned copy (`-v2`)                           |
| Kiro returns empty output         | Re-run with `--verbose`, report output to user                                  |
| Paths in spec do not exist        | Flag them, ask user whether they are `[NEW]` or hallucinated                    |
| Kiro times out                    | Report timeout; offer to write task spec manually using `docs/task-template.md` |
