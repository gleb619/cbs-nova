# T621 — VhsTapeWriter format edge-case specs

## Goal

Add unit specs pinning `VhsTapeWriter` (136 LOC) behavior on format edge cases: unicode payloads,
embedded newlines/quotes in fields, empty event streams, and truncated/mid-write tapes.

## Why

Tape format is a cross-version contract (`docs/vhs-tape-format.md`); replay integrity depends on
writer correctness. Current coverage is happy-path e2e via `LocalFileTapeSinkTest`
(header/events/trailer) — no direct `VhsTapeWriter` refs in specs, no edge-case pinning.

## Acceptance criteria

- [ ] Round-trip: write tape → read back via reader side; content identical for unicode
      (incl. astral-plane chars), CRLF/CR/LF in fields, quotes/backslashes.
- [ ] Empty event stream: header + trailer only — valid tape, readable.
- [ ] Serialization rules pinned: whatever escaping/framing writer uses today, characterization
      style (pin, don't redesign).
- [ ] Truncated-tape behavior documented-by-test if reader side reachable in unit scope.
- [ ] `make lint` passes; targeted test run green.

## Tier

`backend`

## Files to create/modify

- Create: `backend/dsl-starter/starter/src/test/java/cbs/nova/starter/vhs/VhsTapeWriterTest.java`
- Read first: `VhsTapeWriter.java`, `TapeEvent.java`, `TapeHeader.java`,
  `docs/vhs-tape-format.md`, `LocalFileTapeSinkTest.java` for fixtures.

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test --tests 'cbs.nova.starter.vhs.*'
make lint
```

## Constraints

- On work start, load and use skills `caveman` and `codegraph`.
- Tests only — no writer/format changes in this task; format changes would need a new task.

---

You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
