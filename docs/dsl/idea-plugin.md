# dsl-idea-plugin

Thin IntelliJ IDEA plugin so compact DSL/model sources in `src/dsl/` and `src/models/`
don't show as broken Java in the editor. Those compact sources are not valid Java on their own:
the DSL side is preprocessed into real Java by
`backend/dsl-platform/dsl-codegen/src/main/java/cbs/nova/dsl/codegen/preprocessor/DslPreprocessor.java`
(package `cbs.nova.dsl.codegen.preprocessor`) and the model side by `ModelPreprocessor.java` in
the same package, against the `DslCompactSource` contract
(`backend/dsl-platform/dsl-api/src/main/java/cbs/nova/dsl/DslCompactSource.java`, package
`cbs.nova.dsl`). The plugin has no build-time dependency on those classes — it only needs to stop
IDEA's Java plugin from reporting errors on files the compiler rewrites before parsing them.

## What it does

- **Activation gate.** `DslCheckStartupActivity` (a `postStartupActivity`) marks the project as a
  "DSL project" in `DslProjectStateService` when the project dir contains both `src/dsl` and
  `src/models` directories. Every other part of the plugin is a no-op in projects that fail
  this check.
- **File-type interception.** `DslFileTypeOverrider` (a `fileTypeOverrider`) returns
  `CbsDslFileType` for `.java` files inside a module's compact source dirs, so the Java plugin
  never parses them. `CbsDslFileType` is registered with an empty extension list and default
  extension `java`, so it only ever applies via the overrider.
- **Syntax coloring only.** `CbsDslSyntaxHighlighterFactory` delegates to IDEA's
  `JavaFileHighlighter`. There is no PSI parser or semantic model behind the `CbsDsl` language,
  so there is nothing to report errors on — no completion, no go-to-declaration, no refactoring.
- **Dir discovery.** `DslProjectResolverExtension` (a Gradle `projectResolve` extension) requests
  the `cbs.nova.dsl.gradle.tooling.DslProjectModel` Tooling API model, built by
  `backend/dsl-plugins/dsl-gradle-plugin/src/main/java/cbs/nova/dsl/gradle/tooling/DslModelBuilderService.java`
  from the `dslCompile {}` extension. The model exposes `sourceDir`, `dslSubdir`, `modelsSubdir`,
  `outputDir` and `dslPackage`; the resolver uses only `sourceDir` plus the two subdirs
  (`"dsl"` and `"models"` are hardcoded in `DslModelBuilderService`) to build absolute dirs, and
  stores them in a static per-sync-task map with a 10-minute TTL. `DslGradleSyncNotificationListener`
  drains that map on `onSuccess` of a Gradle `RESOLVE_PROJECT` task and hands the dirs to
  `DslSourceRootSyncContributor`.
- **Source-root cleanup.** `DslSourceRootSyncContributor` replaces the contents of the
  project-level `DslSyncedDirs` service and calls `DslSourceRootExcluder`, which *removes* those
  dirs from the module content entries' source folders (inside a `WriteAction`). At startup
  `DslCheckStartupActivity` calls the same excluder with a literal `/src/dsl` / `/src/models`
  path match, so stale source roots from a previous sync are dropped before the first sync
  finishes.
- **Fallback before first sync.** While `DslSyncedDirs` is empty (no successful sync yet),
  `DslFileTypeOverrider` falls back to matching the literal path segments `/src/dsl/` and
  `/src/models/` in the file path.
- **Action.** A **Tools > Compile DSL Sources** action runs `./gradlew :<module>:compileDsl` in
  the project base dir. It needs the IDE's current `Module` (`PlatformCoreDataKeys.MODULE`) and
  is only visible/enabled in an active DSL project.

## What it deliberately does not do

All compilation, validation, and code generation stays owned by `dsl-gradle-plugin`
(`backend/dsl-plugins/dsl-gradle-plugin`) — this plugin is a sync/typing/action layer only.
It does not add the compact dirs as IDE source roots, and it does not register the
`dslCompile` output dir as a generated-sources root (the resolver never reads `outputDir`).
The same compiler is also driven remotely by the `dsl-builder` Spring Boot service; see
`backend/dsl-plugins/dsl-builder/README.md`.

## Build & install

There is no Gradle wrapper in `backend/dsl-plugins`; use the `dsl-platform` wrapper with `-p`:

```bash
backend/dsl-platform/gradlew -p backend/dsl-plugins :dsl-idea-plugin:buildPlugin
```

Produces `backend/dsl-plugins/dsl-idea-plugin/build/distributions/dsl-idea-plugin-<version>.zip`.
In IDEA: **Settings > Plugins > ⚙ > Install Plugin from Disk...** and select the zip. The plugin
targets IntelliJ Community 2023.3.5 with `sinceBuild = 233`.

## Troubleshooting

- **Files still show as broken Java after install**: re-run Gradle sync (**File > Sync Project with
  Gradle Files**); until the first sync completes the plugin falls back to matching literal
  `src/dsl` / `src/models` paths.
- **Nothing happens in a project that has the compact sources**: `DslCheckStartupActivity`
  requires *both* `src/dsl` and `src/models` directly under the project dir. If either is
  missing, the project is not marked as a DSL project and every extension stays inert.
- **Compile DSL Sources does nothing**: the action requires a module in the IDE's current
  context (it resolves `:<module>:compileDsl` from the selected module's name) and an active
  DSL project.

