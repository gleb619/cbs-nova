# T616 — Runbook recipe: promote a definition bundle between environments

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

T569 shipped the Epic 5 environment-promotion flow — backend routes
(`DslPromoteRouterConfiguration`: `GET /api/dsl/promote/environments`,
`GET /api/dsl/promote/definitions`, `POST /api/dsl/promote` with dry-run or apply), BFF proxies
(`/api/v1/dsl/promote*`), and the `promote.vue` UI page. Docs mention it nowhere (grep:
runbook 0, architecture-ui 0 pre-T610).

Add a runbook section "Promote a definition between environments" (style: "Schedule a
definition"):

- [ ] Prerequisites: how environments are configured (read the properties class backing
      `environments()` — verify actual config keys from source; e.g. env list source).
- [ ] `curl GET :3000/api/v1/dsl/promote/environments` → example envelope.
- [ ] Dry-run then apply round-trip: `POST /api/v1/dsl/promote` with realistic body (definition,
      source, target, dry-run flag), both response shapes.
- [ ] What "bundle" includes (definition + drafts? verify from handler/service source) and
      failure modes (target exists, unknown environment, RBAC role required per
      `RbacAuthorizationFilter`).
- [ ] One line pointing to the UI page (`/nova-admin` promote page).
- [ ] Cross-reference the Epic 5 roadmap section.

## Tier

`backend` (docs-only)

## Acceptance criteria

- [ ] Every route, config key, and role claim verified against source — no invented fields.
- [ ] Dry-run vs apply distinction demonstrated with distinct example outputs.
- [ ] `make lint` green.

## Out of scope

- architecture-ui.md page section (T610's successor work owns page sync; do not duplicate).
- Any change to promotion code.

## Files to create/modify

- `docs/runbook.md` (modify — one section)

## Build/test commands

```bash
make lint
```
