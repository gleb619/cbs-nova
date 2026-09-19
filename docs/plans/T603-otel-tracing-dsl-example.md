# T603 — DSL example — DistributedTracingDsl demonstrating OpenTelemetryHelper

## Skills (load on work start)

Load and use `caveman` and `codegraph` before any work on this task.

```
You are not alone, focus on your task, ignore other errors. Keep caveman/ultra-brief responses to user when reporting progress. Minimal texting, save tokens.
```

## Goal

Add a DSL example showcasing the OpenTelemetryHelper's seven modes
(`span` / `endSpan` / `addEvent` / `setBaggage` / `getBaggage` / `injectContext` /
`extractContext`) — currently zero dsl-examples entries demonstrate tracing despite tracing
being default-on in the compose stack (roadmap Epic 4, "Tracing in compose").

Story: a workflow that (1) opens a span, (2) attaches an event, (3) stashes a business key in
baggage, (4) injects W3C trace headers into an outbound HTTP call carrier, (5) extracts the
span-id from a (simulated) inbound header map, (6) reads the baggage back, (7) ends the span.
Return the traceparent + extracted span-id + baggage value so the example run output itself
demonstrates propagation.

Follow the existing example pattern — see `JwtHttpCallDsl`, `HttpResilienceDsl` for helper +
HTTP combination style, and the example test convention in
`AdvancedDslExamplesTest` / examples test module.

## Tier

`backend`

## Acceptance criteria

- [ ] `DistributedTracingDsl` example added alongside existing examples (same source layout).
- [ ] Exercises all seven helper modes; output record carries traceparent, extracted span id,
      baggage round-trip value.
- [ ] Example registered wherever sibling examples are registered (test suite / example module
      list — match `JwtHttpCallDsl` wiring exactly).
- [ ] Unit test asserts the run succeeds and the traceparent matches the W3C pattern
      (`00-<32hex>-<16hex>-<2hex>`); no real OTLP endpoint needed (SDK no-op default).
- [ ] `backend/dsl-starter/gradlew -p backend/dsl-starter test` green.
- [ ] `make lint` green.

## Notes

- Respect documented limitation: no parent-child chaining across helper-initiated spans — the
  example must NOT imply nesting; add a comment in the example source stating this.
- Baggage is JVM-local — the round-trip stays inside one run.

## Out of scope

- Any change to `OpenTelemetryHelper` itself.
- Real collector assertions (compose-level).

## Files to create/modify

- `backend/dsl-starter/starter/src/*/.../examples/DistributedTracingDsl.java` (create — match
  sibling example package)
- Example registration/test file(s) where siblings are wired (modify)

## Build/test commands

```bash
backend/dsl-starter/gradlew -p backend/dsl-starter test
make lint
```
