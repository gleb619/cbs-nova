package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslAuditEntity;
import java.util.List;

/**
 * Paged audit query result: the requested page plus the total number of matching rows (mirrors
 * {@code cbs.nova.dsl.history.DslRunSearchResult}).
 */
public record DslAuditSearchResult(List<DslAuditEntity> items, long total) {
}
