# dsl-idea-plugin

Thin IntelliJ IDEA plugin so compact DSL/model sources in `src/dsl/` and `src/models/`
(see `backend/dsl/src/main/java/cbs/nova/dsl/compact/CompactSourcePreprocessor.java` and
`ModelSourcePreprocessor.java`) don't show as broken Java in the editor.

## What it does

- Intercepts `.java` files under a module's configured `dsl`/`models` source dirs (read from the
  `dslCompile {}` block via a Gradle Tooling API model exposed by `dsl-gradle-plugin`, falling back
  to `src/dsl` / `src/models` before sync completes) and assigns them a dedicated file type instead
  of letting IDEA's Java plugin parse them.
- Applies Java-flavored syntax coloring only — no semantic parser, so there is nothing to error on.
- Marks the dsl/models dirs as source roots and the configured output dir as a generated-sources
  root, for navigation into generated workflow/activity classes.
- Adds a **Tools > Compile DSL Sources** action that runs `./gradlew :<module>:compileDsl`.

## What it deliberately does not do

No code completion, no go-to-declaration, no refactoring, no live templates for DSL/model files.
All compilation, validation, and code generation stays owned by `dsl-gradle-plugin`
(`backend/dsl-gradle-plugin`) — this plugin is a sync/typing/action layer only.

## Build & install

```bash
cd backend
./gradlew :dsl-idea-plugin:buildPlugin
```

Produces a zip under `backend/dsl-idea-plugin/build/distributions/`. In IDEA: **Settings > Plugins
> ⚙ > Install Plugin from Disk...** and select the zip.

## Troubleshooting

- **Files still show as broken Java after install**: re-run Gradle sync (**File > Sync Project with
  Gradle Files**); until the first sync completes the plugin falls back to matching literal
  `src/dsl` / `src/models` paths.
- **Compile DSL Sources does nothing**: the action requires a module selected in the Project view
  (it resolves `:<module>:compileDsl` from the selected module's name).
