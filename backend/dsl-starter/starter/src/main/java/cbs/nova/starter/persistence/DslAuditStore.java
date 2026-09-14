package cbs.nova.starter.persistence;

import cbs.nova.starter.converter.DslAuditMapper;
import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.DslAudit;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Persistence adapter for the append-only {@code dsl_audit} table: keeps the entity-typed
 * {@link DslAuditCrudRepository} behind this boundary so the service and handler layers only see
 * the {@link DslAudit} domain model.
 *
 * <p>
 * Deliberately exposes an append and a paged query only — the audit log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 */
@RequiredArgsConstructor
public class DslAuditStore {

  private final DslAuditCrudRepository repository;
  private final DslAuditMapper mapper;

  /**
   * Appends one audit row. {@code occurredAt} on the given model is used as supplied; the database
   * generates {@code id}.
   */
  public void append(DslAudit row) {
    repository.save(mapper.toEntity(row));
  }

  /**
   * Paged query, newest first ({@code ORDER BY occurred_at DESC, id DESC} — identical to the
   * hand-rolled JDBC repository this store replaces). {@code action}, when non-blank, narrows the
   * result set.
   */
  public DslAuditSearchResult search(@Nullable String action, int offset, int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    // Ordering is fixed by the repository's derived-query method name (occurredAt/id DESC);
    // no separate Sort is needed here.
    Pageable pageable = new OffsetPageRequest(offset, limit, Sort.unsorted());
    Page<DslAuditEntity> page = action != null && !action.isBlank()
            ? repository.findByActionOrderByOccurredAtDescIdDesc(action, pageable)
            : repository.findAllByOrderByOccurredAtDescIdDesc(pageable);
    return new DslAuditSearchResult(
            page.getContent().stream().map(mapper::toDomain).toList(), page.getTotalElements());
  }
}
