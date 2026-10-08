package cbs.nova.starter.persistence;

import cbs.nova.starter.converter.DslAuditMapper;
import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.AuditChainVerification;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Persistence adapter for the append-only {@code dsl_audit} table: keeps the entity-typed
 * {@link DslAuditCrudRepository} behind this boundary so the service and handler layers only see
 * the {@link DslAudit} domain model.
 *
 * <p>
 * Exposes an append and a paged query — the audit log is append-only by convention for all
 * user-facing surfaces — plus a single, narrowly-named maintenance exception:
 * {@link #purgeOlderThan(Instant, int)}. That method exists solely so the opt-in
 * {@code dsl.maintenance.audit-retention} purge (T747) can age rows out of the table once an
 * operator has explicitly enabled it. It is not exposed by any REST handler and not reachable from
 * {@code DslAuditService} or any controller code path.
 *
 * <p>
 * <b>T880:</b> {@link #append(DslAudit)} now extends the tamper-evident hash chain. The read-tail →
 * insert → set-hashes sequence runs inside a single transaction (when a {@link TransactionTemplate}
 * bean is available — see the constructor Javadoc), so the {@code findTail()} read sees its own
 * insert and two concurrent appenders cannot interleave a tail re-read. The one-shot UPDATE on the
 * two hash columns is the documented exception to the append-only invariant — see
 * {@link DslAuditCrudRepository}.
 */
@RequiredArgsConstructor
public class DslAuditStore {

  private static final Logger log = LoggerFactory.getLogger(DslAuditStore.class);

  private final DslAuditCrudRepository repository;
  private final DslAuditMapper mapper;
  @Nullable
  private final TransactionTemplate transactionTemplate;

  /**
   * Convenience constructor used by tests that wire the store by hand without a
   * {@link TransactionTemplate} on the classpath. Production code (see
   * {@code DslRunRepositoryConfiguration#dslAuditStore}) passes the template so append runs inside
   * a transaction.
   */
  public DslAuditStore(DslAuditCrudRepository repository, DslAuditMapper mapper) {
    this(repository, mapper, null);
  }

  /**
   * Appends one audit row, extending the hash chain. {@code occurredAt} on the given model is used
   * as supplied; the database generates {@code id}, then the store computes and writes the
   * {@code prev_hash} / {@code entry_hash} columns. Legacy rows (pre-V4) have both hash columns
   * {@code null}; the next post-migration row links against the tail's {@code entry_hash}, not the
   * first legacy row.
   */
  public void append(DslAudit row) {
    if (transactionTemplate == null) {
      appendChained(row);
      return;
    }
    transactionTemplate.executeWithoutResult(status -> appendChained(row));
  }

  private void appendChained(DslAudit row) {
    DslAuditEntity inserted = repository.save(mapper.toEntity(row));
    String prevHash = repository.findTail().map(DslAuditEntity::entryHash).orElse(null);
    String entryHash = AuditHashChain.computeEntryHash(prevHash, inserted);
    repository.setHashes(inserted.id(), prevHash, entryHash);
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

  /**
   * Maintenance-only: deletes at most {@code batchSize} rows whose {@code occurred_at} is strictly
   * older than {@code cutoff}. Returns the number of rows actually deleted.
   *
   * <p>
   * Sole deletion path for the {@code dsl_audit} table; see the class Javadoc. Callers are expected
   * to invoke this in a loop until it returns less than {@code batchSize}.
   */
  public int purgeOlderThan(Instant cutoff, int batchSize) {
    if (cutoff == null) {
      throw new IllegalArgumentException("cutoff must not be null");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be positive, was " + batchSize);
    }
    return repository.deleteOlderThan(cutoff, batchSize);
  }

  /**
   * T880: walks the {@code dsl_audit} chain in chronological order and reports the first
   * divergence. A row counts as "checked" only when it is post-migration — i.e. has a
   * non-{@code null} {@code entry_hash}. Legacy (pre-V4) rows are skipped: their {@code prev_hash}
   * is ignored and the chain resumes at the first post-migration row, whose {@code prev_hash} must
   * be {@code null}.
   *
   * <p>
   * Stops at the first row whose recomputed hash does not match the stored {@code entry_hash} or
   * whose {@code prev_hash} does not equal the previous row's {@code entry_hash}. The returned
   * {@link AuditChainVerification#ok()} is {@code true} only when every post-migration row matched.
   * An empty table (or a table with only legacy rows) returns {@code ok=true} with
   * {@code checked=0}.
   */
  public AuditChainVerification verifyChain() {
    List<DslAuditEntity> rows = repository.findAllByOrderByOccurredAtAscIdAsc();
    long checked = 0;
    String expectedPrev = null;
    for (DslAuditEntity row : rows) {
      if (row.entryHash() == null) {
        // Legacy row: skip; chain resumes after this one.
        continue;
      }
      if (!java.util.Objects.equals(row.prevHash(), expectedPrev)) {
        return divergence(row, checked,
                "prev_hash does not match the previous row's entry_hash");
      }
      String recomputed = AuditHashChain.computeEntryHash(row.prevHash(), row);
      if (!recomputed.equals(row.entryHash())) {
        return divergence(row, checked,
                "entry_hash does not match sha256(prev_hash || canonical fields)");
      }
      checked++;
      expectedPrev = row.entryHash();
    }
    return new AuditChainVerification(true, checked, null, null, null);
  }

  private static AuditChainVerification divergence(DslAuditEntity row, long checked,
          String reason) {
    String at = row.occurredAt() == null ? null : row.occurredAt().toString();
    return new AuditChainVerification(false, checked, row.id(), at, reason);
  }
}
