# T536 — Unit spec for `FileRepository` (dsl-builder) — path-traversal guard untested

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`FileRepository` (112 L, `dsl-builder` module) has no dedicated test file. It's used with a real
(non-mocked) instance in `FileServiceTest` (`@TempDir`-backed), but that test has zero assertions
touching path-traversal (`..` segments) or the atomic-write behavior — verified via grep, zero
hits for escape/traversal/atomic-move cases anywhere in the module's tests. This class carries a
double-layered path-escape guard (`resolve()`: literal `..` substring check, then a resolved-path
`startsWith(root)` check) and an atomic-write-with-fallback (`ATOMIC_MOVE` → plain `REPLACE_EXISTING`
on `AtomicMoveNotSupportedException`) — both currently unverified by any test.

## Current state

`backend/dsl-plugins/dsl-builder/src/main/java/cbs/nova/dsl/builder/repository/FileRepository.java`:

- `resolve(root, relativePath)`: blank path → `IllegalArgumentException`; normalizes backslashes
  and repeated slashes; literal `..` anywhere in the normalized string → `IllegalArgumentException`
  ("escapes workspace"); resolved+normalized path not under `root` → second
  `IllegalArgumentException` (belt-and-suspenders — verify whether the second check is ever
  reachable once the first rejects all `..`, or whether it catches a different class of escape,
  e.g. an absolute `relativePath` that resolves outside root without containing `..` literally).
- `read`: reads file to string via `BufferedReader`.
- `write`: creates parent dirs, writes to a sibling temp file, atomic-moves into place, falls back
  to non-atomic replace on `AtomicMoveNotSupportedException`, always deletes the temp file in
  `finally` (verify temp file is gone after both the success and the fallback path).
- `list(root, prefix)`: non-directory root → empty list; walks tree, filters `.java` files, filters
  by relative-path prefix when given; sorted by path; swallows `IOException` from `Files.walk` to
  a warn-logged empty list.
- `exists`: `IllegalArgumentException` from `resolve` (e.g. blank path) is caught and turned into
  `false` rather than propagating — the only method with this catch-and-suppress behavior; worth
  confirming that's intentional (a caller checking "does this exist" shouldn't have to catch the
  guard exception, unlike `read`/`write`/`list`).

## Approach

1. `@TempDir`-based tests, real filesystem (matches the module's existing idiom).
2. `resolve` (tested through `read`/`write`/`exists`, whichever method's exception surface is
   clearest): blank path rejected; a normal relative path accepted; `../`-containing path rejected
   at the literal-`..` check; try to construct a case that would only be caught by the
   `startsWith(root)` check (if one exists) to confirm both guards are actually load-bearing, not
   one dead.
3. `write`: content round-trips through `read`; temp file (`*.tmp` sibling) does not linger after
   a successful write; overwrite-existing works; parent directories auto-created.
4. `list`: empty for non-existent/non-directory root; only `.java` files returned; prefix filter
   narrows correctly; sorted by path; a directory containing a non-`.java` file excludes it.
5. `exists`: true for a real file, false for missing, false (not thrown) for a blank/invalid path.

## Acceptance criteria

- [ ] New `FileRepositoryTest` covering all methods above, with explicit path-traversal cases.
- [ ] No production code changes (test-only, unless a genuine escape-guard gap is found — flag
      Blocked with the finding rather than silently hardening it in a test-only task).
- [ ] `:dsl-builder:test` green, `spotlessCheck` passes.

## Files to create/modify (best guess)

- New: `backend/dsl-plugins/dsl-builder/src/test/java/cbs/nova/dsl/builder/repository/FileRepositoryTest.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-builder:test
make lint
```

## Out of scope

- `FileService`/`FileController` (consumers, untouched), other untested `dsl-builder` model/config
  classes surfaced by the same sweep (mostly thin records/`@ConfigurationProperties` — lower value,
  separate rows if ever picked up).
