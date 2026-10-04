package cbs.nova.starter.model;

/**
 * The three workspace entity families the starter file API can operate on.
 *
 * <p>
 * Mirrors {@code cbs.nova.dsl.builder.model.FileEntityKind} so the starter can talk to the
 * dsl-builder generic file endpoints without depending on the builder module.
 */
public enum FileEntityKind {
  DSL, MODEL, DOC
}
