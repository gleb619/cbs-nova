package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to {@code dsl_change_request} (T568). Follows the
 * {@link cbs.nova.starter.persistence.NotificationRuleRepository} idioms: reads via squigglesql,
 * writes delegated to the Spring Data {@link ChangeRequestCrudRepository}, and an explicit
 * {@link RowMapper}.
 */
public class ChangeRequestRepository {

  private static final RowMapper<ChangeRequestEntity> ROW_MAPPER = (rs,
          rowNum) -> new ChangeRequestEntity(
                  rs.getLong("id"),
                  rs.getString("definition_name"),
                  rs.getString("draft_content"),
                  rs.getString("requested_by"),
                  rs.getTimestamp("requested_at").toInstant(),
                  Status.valueOf(rs.getString("status")),
                  rs.getString("approved_by"),
                  rs.getTimestamp("approved_at") == null
                          ? null
                          : rs.getTimestamp("approved_at").toInstant(),
                  rs.getString("comment"));

  private final ChangeRequestCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final ChangeRequestTableColumns T = ChangeRequestTableColumns.of();

  public ChangeRequestRepository(ChangeRequestCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = Objects.requireNonNull(crud);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public long insert(ChangeRequestEntity row) {
    Objects.requireNonNull(row, "row");
    return crud.save(new ChangeRequestEntity(
            row.id(),
            row.definitionName(),
            row.draftContent(),
            row.requestedBy(),
            row.requestedAt(),
            row.status(),
            row.approvedBy(),
            row.approvedAt(),
            row.comment())).id();
  }

  public Optional<ChangeRequestEntity> findById(long id) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r))
            .where(ChangeRequestQueryCriteria.matchesId(T, r, id))
            .build();
    List<ChangeRequestEntity> items = dslQueries.query(query, ROW_MAPPER);
    return items.stream().findFirst();
  }

  public List<ChangeRequestEntity> findByDefinitionName(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r))
            .where(ChangeRequestQueryCriteria.matchesDefinitionName(T, r, definitionName))
            .orderByDesc(r.get(T.requestedAt()))
            .orderByDesc(r.get(T.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public Optional<ChangeRequestEntity> findPendingByDefinitionName(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r))
            .where(ChangeRequestQueryCriteria.matchesDefinitionName(T, r, definitionName))
            .where(ChangeRequestQueryCriteria.matchesStatus(T, r, "PENDING"))
            .orderByDesc(r.get(T.id()))
            .build();
    List<ChangeRequestEntity> items = dslQueries.query(query, ROW_MAPPER);
    return items.stream().findFirst();
  }

  /**
   * Filtered listing, newest first. Both filters are optional; {@code null} means "no filter".
   */
  public List<ChangeRequestEntity> findAll(@Nullable String definitionName,
          @Nullable Status status) {
    var r = T.refer();
    var builder = dslQueries.select()
            .whereIf(definitionName != null,
                    () -> ChangeRequestQueryCriteria.matchesDefinitionName(T, r, definitionName))
            .whereIf(status != null,
                    () -> ChangeRequestQueryCriteria.matchesStatus(T, r, status.name()));

    ExtendedSelectQuery query = builder
            .select(ChangeRequestQueryCriteria.fullSelection(T, r))
            .orderByDesc(r.get(T.requestedAt()))
            .orderByDesc(r.get(T.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public void updateStatus(long id, Status status, @Nullable String approvedBy,
          @Nullable Instant approvedAt, @Nullable String comment) {
    crud.updateStatus(id, status.name(), approvedBy, approvedAt, comment);
  }

  public long count() {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(com.github.squigglesql.squigglesql.literal.Literal.unsafe("COUNT(*)"))
            .build();
    Long total = dslQueries.queryForObject(query, Long.class);
    return total != null ? total : 0;
  }
}
