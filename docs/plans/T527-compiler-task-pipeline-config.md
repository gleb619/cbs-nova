# T527 — Move `DslCompiler` task pipeline into a config class

- **Tier:** backend
- **Status:** Backlog (stub — refine at execution time)

## Goal

`DslCompiler.compileInternal:77` TODO: `move to a config class instead`. The 6-stage
`List<CompileTask>` pipeline (`LoadSourcesTask` → `PreprocessSourcesTask` →
`DescribeDslObjectsTask` → `ValidateDescriptorsTask` → `GenerateCodeTask` → `WriteOutputTask`) is
built inline in `compileInternal`, wiring each task's dependencies by hand at the call site.

## Current state

`backend/dsl-platform/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/DslCompiler.java` —
`compileInternal` constructs the task list inline, each task fed fields already resolved on
`DslCompiler`/`CompileConfig` (`dslSourceCompiler`, `dslPreprocessor`, `codegenNaming`,
`sourcePackageResolver`, `descriptorFactory`, `semanticValidator`, `helperRegistry`,
`processCodeGenerator`, `transactionCodeGenerator`, `generatedClassProviderGenerator`,
`modelRegistryGenerator`, `explainResourceGenerator`, `codeWriter`). `CompileConfig`
(`@Getter @RequiredArgsConstructor`, singleton-per-`Scope` via `SingletonSupport`) already holds
most of these as memoized accessors — precedent for where pipeline construction belongs.

## Approach

1. Add a `compileTasks()`-style accessor on `CompileConfig` (or a small dedicated
   `CompilePipeline`/`CompileTasks` holder alongside it, whichever fits `CompileConfig`'s existing
   `singleton(...)` idiom) that builds and returns the ordered `List<CompileTask>`.
2. `compileInternal` calls that accessor instead of constructing the list inline; loop logic
   (timing, `runTask`, `CompileContext` threading) stays in `DslCompiler` — only the task
   *construction* moves.
3. Preserve exact task order and constructor arguments — zero behavior change.
4. TODO dropped.

## Acceptance criteria

- [ ] TODO removed; `compileInternal` no longer builds the task list literal inline.
- [ ] Task order and wiring identical (existing codegen tests pin output — must stay green
      without edits).
- [ ] `:dsl-codegen:test` green, `make lint` passes.

## Files to create/modify (best guess)

- Modify: `backend/dsl-platform/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/DslCompiler.java`
- Modify: `backend/dsl-platform/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/CompileConfig.java`

## Build/test commands

```bash
backend/dsl-platform/gradlew -p backend/dsl-platform :dsl-codegen:test
make lint
```

## Out of scope

- Changing task order, adding/removing pipeline stages, `CompileContext` shape changes,
  `compile()` public API.
