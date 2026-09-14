# T530 — Unit spec for `DslGitStatusResolver`

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`DslGitStatusResolver` (107 L, starter module) has zero dedicated test file — same shape of gap
T501 already fixed for `dsl-builder`'s `GitStatusService` (JGit status + TTL
`ConcurrentHashMap<Path,Snapshot>` cache), just on the starter side. No test currently exercises
the cache hit/expiry, JGit status aggregation, builder-delegation, or disabled-git paths directly.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/service/DslGitStatusResolver.java`:

- `status(Path candidateDir)`: if a `DslBuilderClient` bean is available, delegates entirely to
  `builder.vcsStatus()` (skips local JGit path); else if git disabled
  (`dslProperties.git().enabled()` false/null) returns `Optional.empty()`; else resolves repo
  root, checks TTL cache, on miss/expiry calls `loadStatus` and caches, swallows any exception
  from `loadStatus` into `Optional.empty()` (logged warn).
- `loadStatus`: JGit `FileRepositoryBuilder.findGitDir` (throws `IOException` if none found),
  aggregates `added`/`changed`/`modified`/`untracked`/`removed`/`missing` into one `dirtyPaths`
  set.
- `repositoryRoot`: configured `dslProperties.git().repositoryDir()` wins over `candidateDir`.
- `ttl()`: `dslProperties.git().statusCacheTtlSeconds()`, default 5s, clamped `Math.max(0, …)`.

## Approach

Follow T501's `GitStatusService` test idiom (tmp-dir JGit repos):

1. Builder-delegation path: mock/stub `DslBuilderClient` present → `vcsStatus()` result returned
   verbatim, JGit never touched.
2. Git-disabled path: `dslProperties.git()` null or `enabled()` false → `Optional.empty()`, no
   JGit call.
3. Clean repo → empty `dirtyPaths`.
4. Dirty repo (added/modified/untracked files) → `dirtyPaths` matches expected set across all six
   JGit status categories.
5. Cache hit within TTL → second `status()` call within the TTL window returns the same snapshot
   without re-invoking JGit (verify via a spy/counter, not timing-flaky sleep if avoidable).
6. Cache expiry → after TTL elapses (inject/fake clock if the class allows, else a short
   configured TTL + short sleep, matching whatever precedent `GitStatusServiceTest` used), a
   rescan happens.
7. No git dir under candidate root → `loadStatus` throws, `status()` returns `Optional.empty()`
   (not propagated).
8. `repositoryRoot` precedence: configured `repositoryDir` overrides `candidateDir` when set.

## Acceptance criteria

- [ ] New `DslGitStatusResolverTest` covering all paths above.
- [ ] No production code changes (test-only, unless a testability seam — e.g. clock injection —
      is genuinely required; note any such change explicitly rather than smuggling it in).
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/service/DslGitStatusResolverTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `DslDefinitionHistoryService`, `RunDefinitionHash`, or other untested classes surfaced by the
  same sweep — separate rows if picked up later.
- `dsl-builder`'s `GitStatusService` (already covered by T501).
