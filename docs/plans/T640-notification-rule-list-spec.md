# T640 — NotificationRuleList component spec

## Goal

Add spec for `frontend/components/src/components/dsl/NotificationRuleList.vue` — the admin
notification rules list. Cover rendering, rule state display, and interaction callbacks
(edit/delete/toggle per whatever the component exposes).

## Why

Components package is broadly tested (82 specs / 96 components) but `NotificationRuleList` is one
of the few logic-bearing uncovered components (the rest are App* layout chrome and a skeleton).
Notification rules are an operator-configured surface — list regressions hide rule state or break
management actions.

## Acceptance criteria

- [ ] Renders rules from props: name/channel/condition fields as the component shows them.
- [ ] Empty state covered.
- [ ] Interaction callbacks fired with correct payload (edit/delete/enable-disable — whatever
      exists in the component's emits).
- [ ] Style follows sibling specs in `frontend/components/src/components/__tests__/`
      (e.g. `ScheduleList`-adjacent specs, `ExecutionList.spec.ts`).
- [ ] `cd frontend && pnpm typecheck && pnpm test` green; `make lint` passes.

## Tier

`frontend`

## Files to create/modify

- Create: `frontend/components/src/components/__tests__/NotificationRuleList.spec.ts`
- Read first: `NotificationRuleList.vue`, one sibling list-component spec for style.

## Build/test commands

```bash
cd frontend && pnpm typecheck && pnpm test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no component changes.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
