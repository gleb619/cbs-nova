package cbs.nova.starter.models;

import java.util.List;

/**
 * Paginated list of DSL execution runs. {@code total} reflects the number of runs matching the
 * filters before the {@code limit} cap is applied.
 */
public record ExecutionListResponse(
        List<ExecutionDto> items,
        int total) {

}
