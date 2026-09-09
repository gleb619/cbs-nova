package cbs.nova.starter.persistence;

import cbs.nova.starter.model.CompileDiagnosticRecord;
import java.util.List;

/**
 * Paged compile diagnostic query result: the requested page plus the total number of matching rows.
 */
public record CompileDiagnosticSearchResult(List<CompileDiagnosticRecord> items, long total) {
}
