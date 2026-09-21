# T629 — Reconcile state-management docs with reality (no Pinia stores)

## Goal

Make the docs describe the actual frontend state pattern. Verified reality: `app/stores/` is
empty (only `.gitkeep`), zero `defineStore` usage, state lives in composables — yet
`@pinia/nuxt` is a dependency and multiple docs instruct adding Pinia stores.

Decide (with human if ambiguity) and apply ONE direction:
- **A (likely):** composables are the state pattern — update docs to say so, delete empty
  `stores/` scaffolding, drop `@pinia/nuxt` from deps and module auto-add logic.
- **B:** Pinia is intended — keep docs, create the first real store migrating an obvious
  stateful composable.

## Why

`frontend/AGENTS.md:195` tells every FE task "Add client state: Add Pinia store in
`admin-ui-plugin/app/stores/`" — a directory that doesn't functionally exist. Task routing and
reviews operate on fiction. Also `CLAUDE.md:12`, `AGENTS.md:15,50,80,94`,
`architecture-ui.md:29,57`.

## Acceptance criteria

- [ ] Direction chosen and applied consistently across: `frontend/AGENTS.md`,
      `docs/architecture-ui.md`, root `CLAUDE.md`.
- [ ] If A: `@pinia/nuxt` removed from `frontend/admin-ui-plugin/package.json` (and lockfile),
      empty `app/stores/` + `.gitkeep` deleted, module docs mention of auto-adding pinia updated.
- [ ] If B: at least one composable migrated to a real store, docs match.
- [ ] `cd frontend && pnpm typecheck && pnpm test` green.
- [ ] `make lint` passes.

## Tier

`frontend`

## Files to create/modify

- Modify: `frontend/AGENTS.md`, `docs/architecture-ui.md`, `CLAUDE.md`
- If A: `frontend/admin-ui-plugin/package.json`, delete `frontend/admin-ui-plugin/app/stores/`,
  module setup code referencing pinia (search `codegraph_search pinia`).

## Build/test commands

```bash
cd frontend && pnpm typecheck && pnpm test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Ask the human which direction if evidence is ambiguous.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
