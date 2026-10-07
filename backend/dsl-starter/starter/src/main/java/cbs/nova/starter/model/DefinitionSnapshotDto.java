package cbs.nova.starter.model;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Payload of {@code GET /api/dsl/definitions/{name}/versions/{hash}} (T875): the stored source
 * snapshot plus a unified line diff against the previous snapshot of the same definition
 * ({@code null} for the first captured snapshot).
 */
public record DefinitionSnapshotDto(
        String name,
        String contentHash,
        String version,
        String capturedAt,
        String capturedBy,
        String sourceText,
        @JsonInclude(JsonInclude.Include.NON_NULL) String diff) {
}
