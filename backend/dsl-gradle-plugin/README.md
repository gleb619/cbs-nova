# DSL Compiler Gradle Plugin

Standalone Gradle plugin that compiles cbs-nova DSL compact source files into Temporal workflow/activity Java sources.

## Source layout

By default the plugin reads sources from the project `src` directory and expects two sub-folders:

```
src/
  dsl/      # compact DSL sources (no package/class declaration)
  models/   # POJO/record sources (no package declaration)
```

Both DSL and model sources are compiled into the package configured by `dslPackage`, so DSL flows can reference model types normally.

## Usage

Apply the plugin in a Java project that contains DSL sources:

```groovy
plugins {
  id 'java'
  id 'cbs.nova.dsl' version '0.0.1-SNAPSHOT'
}

dslCompile {
  dslPackage = 'cbs.nova.dslexamples'
}
```

By default the plugin writes generated sources/classes to `build/generated`. Configure the locations if needed:

```groovy
dslCompile {
  sourceDir = file('src')
  outputDir = file('build/generated-dsl')
  dslVersion = '0.0.1-SNAPSHOT'
  dslPackage = 'cbs.nova.dslexamples'
  buildVersion = 'abc1234'   // defaults to the current git short SHA
  logLevel = 'TRACE'         // defaults to TRACE; passed to the compiler JVM
}
```

The `dslVersion` is used to resolve the compiler runtime dependencies
(`dsl-codegen`, `dsl`, `dsl-api`, `starter`) from Maven Local or any configured repository.

The `buildVersion` is passed to the compiler and becomes the version segment used in generated
Temporal workflow/activity packages (e.g. `cbs.nova.dsl.generated.<flow>.<buildVersion>`). If not
set, the plugin resolves the current git short SHA at execution time.

The `logLevel` is passed to the compiler process as a JVM system property and as the last
positional argument. It controls the SLF4J/simple logger level. The default is `TRACE`.

By default the plugin adds `cbs.nova:starter:${dslVersion}` to the `dslCompiler` configuration
so the compiler can resolve the runtime conventions contributed by `starter`. Configure which
runtime module is added via the `runtimeModule` property:

```groovy
dslCompile {
  runtimeModule = 'dsl-runtime'          // replaces 'starter' (default)
  runtimeModule = ''                     // opt out: add the runtime dependency manually
}
```

When `runtimeModule` is empty the plugin adds no automatic runtime dependency. Declare the
runtime modules you need directly on the `dslCompiler` configuration:

```groovy
dependencies {
  dslCompiler project(':dsl-runtime')
}
```

The same `runtimeModule` property is wired onto the `compileDsl` task via convention, so the
configured runtime is visible to the task's input fingerprint.

## Tasks

- `compileDsl` — compacts DSL and model sources with `cbs.nova.dsl.codegen.DslCompiler`
- `copyGeneratedClasses` — copies `.class` files produced by the compiler into the Java compile output so they are packaged in the jar

`compileDsl` is automatically wired before `compileJava` and the generated output directory is added to the main source set.

## Development inside cbs-nova

Because `dsl-examples` applies this plugin, the plugin and its dependencies are resolved through
the root project's dependency substitution rules. Build as usual:

```bash
./gradlew :dsl-gradle-plugin:build
./gradlew :dsl-examples:compileDsl
```
