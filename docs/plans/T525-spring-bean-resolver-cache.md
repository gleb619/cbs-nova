# T525 — `SpringBeanResolver` — Caffeine-cache resolved DSL beans

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`resolve(Class<?>)` hits `ApplicationContext.getBean(type)` on every call, per the file's own
TODO ("add some guard ... app.yml with caffeine memoize"). DSL objects call bean resolution
repeatedly at runtime for the same handful of types (helpers, resolvers, launchers) — cache the
`Class<?> → Object` lookup so steady-state resolves skip the Spring context call.

## Current state

`backend/dsl-starter/starter/src/main/java/cbs/nova/starter/resolver/SpringBeanResolver.java`
— single `resolve` method, no cache, `NoSuchBeanDefinitionException` → `IllegalStateException`.
Sole construction site: `DslConfiguration.java:155` (`new SpringBeanResolver(applicationContext)`).
No existing test file for this class (verify before writing one).

## Approach

1. Add a Caffeine `Cache<Class<?>, Object>` (bounded size, e.g. ~256 — beans are singletons so a
   small bound is plenty; follow the `JacksonJsonSchemaGenerator`/T423 Caffeine idiom already in
   the codebase for defaults/config shape).
2. Cache only successful resolves — a bean not yet registered (unlikely post-startup, but don't
   cache the negative/exception case) still throws fresh each time.
3. `app.yml`-configurable cache size only if the codebase's existing Caffeine-config convention
   (see `JacksonJsonSchemaGenerator`/T423, T496) calls for it — otherwise a fixed bound is fine;
   don't invent new config surface beyond what precedent sets.
4. TODO dropped.

## Acceptance criteria

- [ ] TODO removed.
- [ ] `resolve(type)` called twice for the same type hits `ApplicationContext` only once (cache
      identity/behavior test).
- [ ] Unknown-type error path unchanged (message + exception type), not cached.
- [ ] `:starter:test` green, `make lint` passes.

## Files to create/modify (best guess)

- Modify: `backend/dsl-starter/starter/src/main/java/cbs/nova/starter/resolver/SpringBeanResolver.java`
- Test: new `SpringBeanResolverTest` (none exists today — verify).

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-starter :starter:test
make lint
```

## Out of scope

- `ExplainResourceRegistry`'s separate (and unrelated) caffeine-memoize TODO — that class is
  `@Deprecated(forRemoval=true)`, part of the T513 explain-pathway retirement, not this concern.
- `BeanResolver` interface change, `DslConfiguration` wiring changes.
