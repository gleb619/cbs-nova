package cbs.nova.starter.model;

import java.util.List;

public record ExecutionListResponse(
        List<ExecutionDto> items,
        int total) {

}
