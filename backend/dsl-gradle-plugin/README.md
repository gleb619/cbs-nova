# DSL Compiler Gradle Plugin

Standalone Gradle plugin that compiles cbs-nova DSL compact source files into Temporal workflow/activity Java sources.

## Usage

Apply the plugin in a Java project that contains DSL sources:

```groovy
plugins {
  id 'java'
  id 'cbs.nova.dsl' version '0.0.1-SNAPSHOT'
}
```

By default the plugin reads compact DSL sources from `src` and writes generated
sources/classes to `build/generated`. Configure the locations if needed:

```groovy
dslCompile {
  sourceDir = file('src/dsl')
  outputDir = file('build/generated-dsl')
  dslVersion = '0.0.1-SNAPSHOT'
}
```

The `dslVersion` is used to resolve the compiler runtime dependencies
(`dsl-codegen`, `dsl`, `dsl-api`, `starter`) from Maven Local or
any configured repository.

## Tasks

- `compileDsl` — compacts sources with `cbs.nova.dsl.codegen.DslCompiler`
- `copyGeneratedClasses` — copies `.class` files produced by the compiler into
  the Java compile output so they are packaged in the jar

`compileDsl` is automatically wired before `compileJava` and the generated
output directory is added to the main source set.

## Development inside cbs-nova

Because `dsl-examples` applies this plugin, the plugin and its dependencies must
first be published to Maven Local:

```bash
./gradlew :dsl-api:publishToMavenLocal :dsl:publishToMavenLocal \
  :dsl-codegen:publishToMavenLocal \
  :starter:publishToMavenLocal :dsl-gradle-plugin:publishToMavenLocal
```

After publishing you can build the consumer as usual:

```bash
./gradlew :dsl-examples:compileDsl
```

The root build substitutes the external coordinates back to local projects, so
the plugin itself is compiled against the current source tree rather than the
published artifacts.
