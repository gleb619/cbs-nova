# T532 — Unit spec for `RunTimeseriesBucketing.foldMinuteBuckets`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`RunTimeseriesBucketing` (38 L, pure static logic, zero I/O) has no test file and no indirect
coverage either — sole caller `JdbcDslRunRepository` has no test referencing it (verified via
grep — only occurrence outside its own file). It folds per-minute run-count rows into
wider buckets (e.g. 5-min/1-hour) for timeseries charts — integer-division bucket-index math,
per-status count merging, and output sort order are all easy to get subtly wrong and currently
unverified.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/persistence/RunTimeseriesBucketing.java`
— `foldMinuteBuckets(List<RunTimeseriesBucket> minuteRows, Instant windowStart, long
bucketSeconds)`:

1. For each minute row, compute `bucketIndex = secondsFromStart / bucketSeconds` (integer
   division — floor behavior for negative offsets unverified).
2. Merge counts per `(bucketIndex, status)` via `Long::sum`.
3. Reconstruct `bucketStart = windowStart + bucketIndex*bucketSeconds` per bucket.
4. Sort output by `bucketStart` then `status`.

`RunTimeseriesBucket` is presumably a small record (`bucketStart`, `status`, `count`) — confirm
its exact shape before writing tests.

## Approach

1. Read `RunTimeseriesBucket`'s actual field/record shape first.
2. Empty input → empty output.
3. Single minute row → single bucket, same status/count.
4. Multiple minute rows within the same wider bucket, same status → counts summed.
5. Multiple minute rows within the same wider bucket, different statuses → separate output
   entries for the same `bucketStart`.
6. Rows spanning bucket boundaries → correctly split into separate buckets (verify off-by-one at
   the exact boundary second).
7. Output sort order verified: multiple buckets × multiple statuses → sorted by `bucketStart` then
   `status` lexicographically.
8. A row with `bucketStart` before `windowStart` (if that's a real possible input) — verify actual
   behavior (negative bucket index) rather than assuming; document what happens.

## Acceptance criteria

- [ ] New `RunTimeseriesBucketingTest` covering all cases above.
- [ ] No production code changes (test-only, unless a genuine bug is found — if so, flag Blocked
      with the finding rather than silently "fixing" folding math in a test-only task).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/RunTimeseriesBucketingTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `JdbcDslRunRepository` (consumer, untouched), other untested `persistence/` classes
  (`DslRunQueryCriteria`, `OffsetPageRequest`, `DslAuditStore` — separate rows if picked up later).
