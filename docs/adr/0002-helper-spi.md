# 0002. Wire `@Helper` implementations through a generated SPI, not reflection

- **Status:** Accepted
- **Date:** 2026-09-02 (retroactive — records a foundational decision)

## Context

DSL Processes, Transactions, and Functions call **Helpers** — plain Java classes annotated
`@Helper(name = "…")` that implement `Executable<In, Out>` — for reusable business logic
(`base64`, `regex`, `hmacSha256Sign`, `httpCall`, …). The runtime has to turn a helper *name* used
in a DSL definition into a helper *instance* at execution time.

The obvious implementation is reflection: scan the classpath for `@Helper`, and
`Class.newInstance()` on demand. That works but has costs that matter here:

- classpath scanning and reflective construction are slow at startup and hostile to GraalVM
  native-image / AOT;
- reflection hides the helper set from the compiler — a missing or mis-wired helper is a runtime
  failure, not a build failure;
- helpers have varied construction needs (`JsonExtractHelper` needs an `ObjectMapper`,
  `HttpCallHelper` needs an `HttpClient` and logging properties, `@SpringHelper` classes are Spring
  beans), which a single reflective `newInstance()` cannot express.

## Decision

We will resolve helpers through **generated code**, not reflection. The annotation processor
`backend/dsl-platform/misc-codegen/.../HelperSpiProcessor` reads `@Helper` / `@SpringHelper` at
build time and emits two classes:

- a `GeneratedHelperResolver` that maps helper name → helper `Class`, and
- a `GeneratedHelperInstanceResolver` that constructs each helper explicitly
  (`new Base64Helper()`, `new JsonExtractHelper(new ObjectMapper())`, …).

The generated factories are discovered at runtime via `java.util.ServiceLoader`. Resolution order
is: Spring bean first (for `@SpringHelper` / `componentModel = LAZY`), then the generated factories.
**There is no reflection fallback** — if neither source can provide a helper, resolution fails
loudly rather than silently degrading.

## Consequences

**Positive**

- The helper set is fixed and visible at compile time; a broken wiring is a build error.
- No classpath scanning, no reflective construction — startup is fast and the path is AOT-friendly.
- Each helper's real constructor is called, so per-helper dependencies are explicit.

**Negative**

- Adding a helper requires the annotation processor to run; a stale `build/generated/…` tree
  produces confusing "cannot resolve helper" errors until a rebuild.
- Test code that instantiates helpers without the generated factory (e.g. the hand-rolled
  if-chains in `AdvancedDslExamplesTest` / `IntermediateDslExamplesTest`) must be updated by hand
  for every new helper, or unrelated preview/reload tests fail via the `DslConfig` singleton.
- The `misc-codegen` processor is now on the critical path for every backend build.

**Neutral**

- `@SpringHelper` is a thin meta-annotation over `@Helper`; the two annotations share the processor
  and differ only in `componentModel`.
