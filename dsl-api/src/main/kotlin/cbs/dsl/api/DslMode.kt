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
