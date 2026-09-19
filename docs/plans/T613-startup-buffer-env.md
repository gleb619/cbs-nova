# T613 — StarterApplication startup-buffer: ENV support + constant to StarterConstants

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Resolve the TODO at
`backend/dsl-starter/starter-launcher/src/main/java/cbs/nova/starter/StarterApplication.java:39`:

```java
//TODO: add ENV support, move magic code to a `cbs.nova.starter.core.StarterConstants`
```

`bufferCapacity()` reads only system property `cbs.startup.buffer.capacity` with a magic
`10_000` default inlined in the launcher. Task:

1. Move the default + the lookup keys into `StarterConstants`
   (`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/StarterConstants.java`)
   following the existing constant style there.
2. Precedence: system property → ENV var (`CBS_STARTUP_BUFFER_CAPACITY`, Spring-style relaxed
   uppercase of the property) → default. Use `System.getenv` with a constant key, matching the
   `VhsCallDrivers` precedent (`VhsCallDrivers.java:41`).
3. Malformed values: fall back to the default with a single warn log (don't crash startup on a
   bad ENV).

## Tier

`backend`

## Acceptance criteria

- [ ] `StarterConstants` carries the default capacity + property key + env key; launcher has no
      magic numbers.
- [ ] System property still wins; ENV var consulted second; default last — precedence asserted
      in a unit test (launcher class is testable via the static `bufferCapacity()` package-private
      method; set/restore system property + env stubbing per existing test conventions).
- [ ] Malformed ENV (e.g. `abc`) → default + warn, covered by test.
- [ ] TODO removed.
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter :starter:test :starter-launcher:test`
      green (whichever test source sets exist for the launcher).
- [ ] `make lint` green.

## Out of scope

- Spring `@ConfigurationProperties` binding (main runs pre-context; plain getenv is right).

## Files to create/modify

- `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/core/StarterConstants.java` (modify)
- `backend/dsl-starter/starter-launcher/src/main/java/cbs/nova/starter/StarterApplication.java` (modify)
- Test for `bufferCapacity()` (create/modify in launcher or starter test tree)

## Build/test commands

```bash
backend/dsl-starter/gradlew -p backend/dsl-starter test
make lint
```
