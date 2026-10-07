package cbs.nova.starter.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response shape returned when a DSL run is started by rerun or by the runner page.
 *
 * <p>
 * Carries the new run id and, for reruns, the source run id so lineage is queryable.
 */
@Schema(description = "New DSL run started from a rerun request")
public record RunStartResponse(
        @Schema(description = "New run id") String runId,
        @Schema(description = "Source run id when this run is a rerun") String rerunOf) {
}
