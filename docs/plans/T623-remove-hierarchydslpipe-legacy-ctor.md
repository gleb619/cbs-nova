# T623 — Remove HierarchyDslPipe legacy test-only constructor

## Goal

Delete the 10-arg legacy constructor on `HierarchyDslPipe` (delegates with `objectGuard = null`)
and its `//TODO: remove constructor, update related tests` marker; migrate the 8 test call sites
to the full constructor with an explicit `null` (or a lightweight `ManifestObjectGuard` test
double where the test exercises enforcement).

## Why

`HierarchyDslPipe.java:49` TODO: the legacy ctor exists only for tests that skip object-level
enforcement. Main wiring (`TemporalConfiguration`) uses the full constructor. Dead overload hides
the guard parameter from test authors.

## Call sites to migrate (8)

- `starter/src/test/.../IntermediateDslExamplesTest.java:76`
- `starter/src/test/.../DevDslRuntimeErrorHandlingTest.java:67`
- `starter/src/test/.../DevDslRuntimeMetricsTest.java:63`
- `starter/src/test/.../DevDslRuntimeTest.java:67`
- `starter/src/test/.../PreviewTimeoutTest.java:185`
- `starter/src/test/.../core/pipe/HierarchyDslPipeTest.java:113`
- `starter/src/test/.../DslStarterIntegrationTest.java:77`
- `starter/src/test/.../DevDslRuntimeCachingTest.java:80`

Verify `TemporalConfiguration.java:257` already uses the 11-arg ctor; if not, migrate it too.

## Acceptance criteria

- [ ] Legacy constructor + TODO comment deleted from `HierarchyDslPipe`.
- [ ] All 8 test call sites compile and pass against the full constructor.
- [ ] `HierarchyDslPipeTest` still covers both guard-present and guard-absent branches.
- [ ] Targeted test run green (`:starter:test --tests '*DevDslRuntime*' --tests '*HierarchyDslPipe*' --tests 'DslStarterIntegrationTest' --tests 'PreviewTimeoutTest' --tests 'IntermediateDslExamplesTest'`).
- [ ] `make lint` passes.

## Tier

`backend`

## Files to create/modify

- Modify: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/pipe/HierarchyDslPipe.java`
- Modify: the 8 test files listed above.
- Possibly: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/TemporalConfiguration.java`.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Pure deletion + call-site migration — no behavior change.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
