package cbs.nova.starter.persistence;

import cbs.nova.starter.model.DslAudit;
import java.util.List;

/**
 * Paged audit query result: the requested page plus the total number of matching rows (mirrors
 * {@code cbs.nova.dsl.history.DslRunSearchResult}).
 */
public record DslAuditSearchResult(List<DslAudit> items, long total) {
}
