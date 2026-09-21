# T637 — mergeRecords @Helper (shallow/deep record merge)

## Goal

Add `MergeRecordsHelper` to the `starter` module: DSL-callable merge of two or more
`Map<String, Object>` records with two modes — `shallow` (last-write-wins per top-level key) and
`deep` (recursive merge; maps merged key-wise, non-map values overwritten, lists replaced).

## Why

No merge primitive exists: `ListOpsHelper` modes are pluck/flatten/distinct/groupby/countby/
sumby/minby/maxby; `JsonPatchHelper` applies RFC-6902 patches (different operation). Combining
outputs of two httpCalls or a call + defaults currently requires manual per-key interpolation.

## Acceptance criteria

- [ ] `MergeRecordsHelper` accepts `records` (list, ≥1) + `mode` (default `shallow`).
- [ ] Shallow: later records overwrite same keys; single record returns equivalent map.
- [ ] Deep: nested maps merged recursively; null values per convention (follow sibling
      null-handling, e.g. ListOps `IllegalArgumentException` naming style).
- [ ] Collision policy for deep mode with map-vs-scalar documented + tested (scalar wins,
      pinned as characterization decision).
- [ ] Unit tests follow a sibling spec (e.g. `ListOpsHelperTest`); registered where siblings
      register.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/helper/MergeRecordsHelper.java`
- Create: model records (follow `ListOpsIn/Out` placement)
- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/helper/MergeRecordsHelperTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests '*MergeRecords*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Follow sibling helper structure exactly — no new patterns.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
