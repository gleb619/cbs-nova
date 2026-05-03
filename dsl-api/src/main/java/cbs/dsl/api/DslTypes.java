package cbs.dsl.api;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/** Consolidated DSL types for mode and import resolution. */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DslTypes {

  /** DSL execution mode controlling how code-based imports are resolved. */
  public enum DslMode {
    /**
     * Production mode. Code imports resolve exclusively from SPI-registered
     * {@link cbs.dsl.impl.ImplRegistry} entries. Runtime classpath scanning is disabled.
     */
    STRICT,

    /**
     * Development mode. Falls back to runtime classpath scanning via
     * {@link cbs.dsl.compiler.CodeImportResolver} when SPI registry is unavailable. Slower startup
     * but supports ad-hoc .kts development.
     */
    LENIENT,
  }

  /** Defines the type of an import directive in a DSL script. */
  public enum ImportType {
    /** Imported from another DSL script (.kts file). */
    DSL,

    /** Imported from compiled Kotlin/Java code (classes annotated with @DslComponent). */
    CODE,
  }
}
