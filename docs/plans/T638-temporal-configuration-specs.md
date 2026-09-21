# T638 — TemporalConfiguration wiring specs

## Goal

Add an auto-configuration test for `TemporalConfiguration` (397 LOC, 24 `@Bean` methods) —
currently touched only indirectly by `DslRootAutoConfigurationTest`.

## Why

Largest untested config class in the starter. Bean wiring regressions (worker options, workflow
client, pipe construction, guards) surface only in full integration runs — slow and
inconsistent. A focused auto-config spec pins: which beans exist under which properties,
conditional behavior (e.g. disabled mode, guard present/absent), and bean types.

## Acceptance criteria

- [ ] `TemporalConfigurationTest` (ApplicationContextRunner style, following
      `DslRootAutoConfigurationTest`) pins bean presence/types for the main @Bean surface.
- [ ] At least two conditional paths exercised (e.g. property off / on, guard bean absent).
- [ ] No live Temporal required — stub/fake service beans or `ApplicationContextRunner`
      property-driven assertions only.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/config/TemporalConfigurationTest.java`
- Read first: `TemporalConfiguration.java`, `DslRootAutoConfigurationTest.java` for style.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.config.TemporalConfigurationTest'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no config changes. Scope to wiring assertions, not behavior (integration suite
      owns behavior).

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
