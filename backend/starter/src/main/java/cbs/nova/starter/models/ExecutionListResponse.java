package cbs.nova.starter.models;

import java.util.List;

public record ExecutionListResponse(
        List<ExecutionDto> items,
        int total) {

}
