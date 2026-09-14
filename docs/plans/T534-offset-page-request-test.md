# T534 — Unit spec for `OffsetPageRequest`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`OffsetPageRequest` (73 L, `persistence` package, sole consumer `DslAuditStore`) implements Spring
Data's `Pageable` with an arbitrary row offset instead of a page-number multiple — no test file,
zero indirect coverage. A likely correctness gap: `previousOrFirst()` computes `offset - limit`
whenever `hasPrevious()` (`offset > 0`) is true, but `offset > 0 && offset < limit` (e.g.
`offset=5, limit=10`) makes that arithmetic go negative — `previousOrFirst()` would then construct
an `OffsetPageRequest` with a **negative offset** instead of clamping to `first()`. This needs
verifying, not assuming — write the test first to confirm the gap actually reproduces before
treating it as a bug.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/persistence/OffsetPageRequest.java`
— package-private, implements `Pageable`:

- `getPageNumber()` = `offset / limit` (integer division — truncates for non-multiple offsets, as
  intended per the class javadoc).
- `next()` = `offset + limit`.
- `previousOrFirst()` = `hasPrevious() ? new OffsetPageRequest(offset - limit, ...) : first()`.
- `first()` = `offset=0`.
- `withPage(pageNumber)` = `pageNumber * limit`.
- `hasPrevious()` = `offset > 0`.

## Approach

1. Construction + basic accessors: `getOffset()`, `getPageSize()`, `getSort()` return what was
   passed in.
2. `getPageNumber()`: exact multiple (`offset=20, limit=10` → 2) and non-multiple (`offset=25,
   limit=10` → 2, truncated) — confirm truncation is the intended/documented behavior, not an
   oversight.
3. `next()`: offset advances by exactly `limit`, sort/limit preserved.
4. `first()`: offset resets to 0.
5. `withPage(n)`: offset becomes `n * limit`.
6. `hasPrevious()`: false at `offset=0`, true for any `offset > 0`.
7. `previousOrFirst()` — the focal case:
   - `offset >= limit` (e.g. `offset=20, limit=10`): steps back cleanly to `offset=10`.
   - `0 < offset < limit` (e.g. `offset=5, limit=10`): **verify actual returned offset**. If it's
     negative, that's a real bug — flag Blocked with the finding and a suggested fix (clamp to
     `first()` when `offset - limit < 0`, i.e. `previousOrFirst()` should probably check
     `offset >= limit` rather than `offset > 0`), don't silently patch production code in what
     started as a test-only task without calling it out explicitly in the commit/PR.
   - `offset == 0`: `hasPrevious()` false → returns `first()`.

## Acceptance criteria

- [ ] New `OffsetPageRequestTest` covering all methods above.
- [ ] The `previousOrFirst()` mid-range case is explicitly asserted (not skipped) — either it
      passes cleanly (bug doesn't reproduce, document why) or it's flagged as a real finding.
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/OffsetPageRequestTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `DslAuditStore` (consumer, untouched unless the `previousOrFirst()` finding requires a fix
  there too — note explicitly if so). Other untested `persistence/` classes (`DslRunQueryCriteria`,
  `DslAuditStore` itself — separate rows if picked up later).
