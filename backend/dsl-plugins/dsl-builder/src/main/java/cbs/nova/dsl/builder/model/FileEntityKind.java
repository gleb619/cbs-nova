package cbs.nova.dsl.builder.model;

/**
 * The three workspace entity families the generic file API can operate on.
 *
 * <p>
 * Used by {@link cbs.nova.dsl.builder.service.FileService} and
 * {@link cbs.nova.dsl.builder.controller.FileController} so DSL sources, models and docs reuse the
 * same read/write/list/flush pipeline.
 */
public enum FileEntityKind {
  DSL, MODEL, DOC
}
