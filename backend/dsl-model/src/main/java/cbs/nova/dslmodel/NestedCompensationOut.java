package cbs.nova.dslmodel;

import java.util.List;

public record NestedCompensationOut(String jobId, String status,
        List<CompensationLogEntry> compensationLog) {
}
