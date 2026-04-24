package cbs.dsl.api

/**
 * DSL execution mode controlling how code-based imports are resolved.
 */
enum class DslMode {
  /**
   * Production mode. Code imports resolve exclusively from SPI-registered
   * [ImplRegistry] entries. Runtime classpath scanning is disabled.
   */
  STRICT,

  /**
   * Development mode. Falls back to runtime classpath scanning via
   * [cbs.dsl.compiler.CodeImportResolver] when SPI registry is unavailable.
   * Slower startup but supports ad-hoc .kts development.
   */
  LENIENT,
}

/**
 * Defines the type of an import directive in a DSL script.
 */
enum class ImportType {
  /**
   * Imported from another DSL script (.kts file).
   */
  DSL,

  /**
   * Imported from compiled Kotlin/Java code (classes annotated with @DslComponent).
   */
  CODE,
}
