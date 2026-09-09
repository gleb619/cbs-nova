package cbs.nova.starter.model;

/**
 * Source of a persisted compile diagnostic. Stored as the enum name in the
 * {@code dsl_compile_diagnostics.source} column.
 */
public enum CompileDiagnosticSource {
  RELOAD, PUBLISH, DRAFT
}
