# T535 — Unit spec for `DslRunQueryCriteria`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`DslRunQueryCriteria` (60 L, package-private SQL-criteria factory, sole consumer
`JdbcDslRunRepository`) has no dedicated test file. It's only reachable today through
`JdbcDslRunRepository`'s broader search/list tests, which exercise end-to-end query results but
may not specifically pin the two subtlest pieces of logic here: the case-insensitive matching
(`LOWER(...)` both sides) and the mode-fallback `NULLIF(execution_mode, '') → COALESCE(..., 'RUN')`
chain in `matchesModeIgnoreCase` (blank/empty `execution_mode` should match a search for `"RUN"`).
Verify whether existing `JdbcDslRunRepositoryTest`/`DslRunRepositoryIntegrationTest` coverage
already exercises this specific fallback before writing redundant tests — if it does, scope down
to whatever's genuinely missing rather than duplicating.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/persistence/DslRunQueryCriteria.java`
— static factory methods building `squigglesql` `Criteria`/`FunctionCall` fragments:
`matchesProcessName`, `matchesStatusIgnoreCase`, `matchesModeIgnoreCase` (the NULLIF/COALESCE
chain), `matchesCorrelationId`, `fullSelection` (column list), `minuteBucket` (`date_trunc`),
`lower` (helper).

## Approach

Follow T502's `ExtendedSelectQuery` precedent — render each criteria fragment to its SQL string
(via whatever `squigglesql` render entry point `ExtendedSelectQuery`'s existing test already uses)
and assert the rendered text, rather than executing against a real DB (that's
`JdbcDslRunRepositoryTest`'s job):

1. `matchesProcessName` — renders an equality predicate against the right column.
2. `matchesStatusIgnoreCase` — both sides wrapped in `LOWER(...)`.
3. `matchesModeIgnoreCase` — full `LOWER(COALESCE(NULLIF(execution_mode, ''), 'RUN')) = LOWER(?)`
   shape (verify exact rendered form, don't assume).
4. `matchesCorrelationId` — plain equality.
5. `fullSelection` — returns all expected columns, in order, matching `DslRunTableColumns`'s full
   field set (catches a column silently dropped/added without updating this list).
6. `minuteBucket` — renders `date_trunc('minute', started_at)` shape.
7. If a DB-backed test already covers the mode-fallback semantics behaviorally (blank mode row
   matches `"RUN"` search), note that and keep this task to the parts genuinely uncovered
   (`fullSelection` column-drift protection is likely the highest-value piece regardless).

## Acceptance criteria

- [ ] New `DslRunQueryCriteriaTest` covering the criteria above (scope adjusted per the overlap
      check against existing `JdbcDslRunRepositoryTest`/`DslRunRepositoryIntegrationTest`
      coverage).
- [ ] No production code changes (test-only, unless a genuine rendering bug surfaces — flag
      Blocked).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/persistence/DslRunQueryCriteriaTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `JdbcDslRunRepository` (consumer, untouched), `DslRunTableColumns`, `DslAuditStore` (separate
  row if picked up later).
