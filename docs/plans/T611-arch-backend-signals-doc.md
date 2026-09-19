# T611 — architecture-backend.md: document the Temporal signals bridge (T567)

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

T567 (commit `3110fce4`) shipped the roadmap Epic 3 "Temporal signals bridge" —
declare/await signals in `ProcessBuilder` (`SignalDescriptor`, `SignalAwaiter`,
`ProcessContext` +33 lines), codegen in `ProcessCodeGenerator` (+121), delivery + query routes
(`DslSignalsRouterConfiguration`, `DslSignalsHandler`, RBAC wiring), a FE signals panel,
`SignalProbeDsl` example, and a 224-line `SignalProbeDslIntegrationTest`. T568's approval gate
got a full architecture-backend.md paragraph (~line 205); the signals bridge got **zero**
mentions.

Add a "Signals" subsection (likely near "Scheduling", ~line 209) covering:

- [ ] DSL author surface: how a process declares a signal and awaits it (builder methods,
      `SignalDescriptor` shape) — verified from `ProcessBuilder`/`SignalProbeDsl` source.
- [ ] Generated Temporal mechanics: what the codegen emits (signal handler methods, await
      blocking) — from `ProcessCodeGenerator`.
- [ ] REST surface: exact routes + verbs from `DslSignalsRouterConfiguration` (delivery,
      query), RBAC roles required, unified error envelope behavior.
- [ ] Manifest interaction: the `app/dsl/src/main/resources/piece-manifest.yaml` +13 lines —
      what pieces the bridge registers.
- [ ] FE pointer: which panel surfaces signals (one line + link to architecture-ui.md once
      T610 lands).
- [ ] Example + IT pointer: `SignalProbeDsl`, `SignalProbeDslIntegrationTest`.

Match the T568 paragraph style: dense prose, exact config keys, route table if >2 routes.

## Tier

`backend` (docs-only)

## Acceptance criteria

- [ ] New section present; every route/config/builder claim verified against source (cite file
      + line in the doc where the existing style does).
- [ ] No changes to other sections except a one-line entry in the doc's navigation/index if one
      exists.
- [ ] `make lint` green.

## Out of scope

- architecture-ui.md (T610 owns page sync).
- VHS/manifest docs.

## Files to create/modify

- `docs/architecture-backend.md` (modify)

## Build/test commands

```bash
make lint
```
