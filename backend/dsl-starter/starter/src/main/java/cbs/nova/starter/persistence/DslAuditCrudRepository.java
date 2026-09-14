package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.DslAuditEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/**
 * Spring Data JDBC access to the append-only {@code dsl_audit} table.
 *
 * <p>
 * Deliberately extends the plain {@link Repository} marker instead of {@code CrudRepository}: the
 * latter would drag in {@code delete}/{@code count} methods that violate the append-only invariant
 * — the audit log is insert + read only. Exposing exactly {@code save} plus the paged finders keeps
 * that guarantee at compile level; no mutation method is reachable.
 *
 * <p>
 * Both finders order by {@code occurred_at DESC, id DESC}, matching the {@code ORDER BY} clause of
 * the hand-rolled JDBC repository this interface replaces.
 */
public interface DslAuditCrudRepository extends Repository<DslAuditEntity, Long> {

  DslAuditEntity save(DslAuditEntity entity);

  Page<DslAuditEntity> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);

  Page<DslAuditEntity> findByActionOrderByOccurredAtDescIdDesc(String action, Pageable pageable);
}
