package cbs.nova.starter.model;

/**
 * One row of {@code GET /api/dsl/definitions/{name}/versions} (T875): the content hash a definition
 * was captured with plus capture metadata.
 */
public record DefinitionVersionDto(
        String contentHash,
        String version,
        String capturedAt,
        String capturedBy) {
}
