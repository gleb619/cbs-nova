package cbs.dsl.api;

/**
 * DSL execution mode controlling how code-based imports are resolved.
 */
public enum DslMode {
  /**
   * Production mode. Code imports resolve exclusively from SPI-registered
   * {@link cbs.dsl.impl.ImplRegistry} entries. Runtime classpath scanning is disabled.
   */
  STRICT,

  /**
   * Development mode. Falls back to runtime classpath scanning via
   * {@link cbs.dsl.compiler.CodeImportResolver} when SPI registry is unavailable.
   * Slower startup but supports ad-hoc .kts development.
   */
  LENIENT,
}
