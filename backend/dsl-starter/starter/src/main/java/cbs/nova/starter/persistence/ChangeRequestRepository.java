package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.SqlParameterValue;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

/**
 * JDBC access to {@code dsl_change_request} (T568). Follows the
 * {@link cbs.nova.starter.persistence.NotificationRuleRepository} idioms: constructor injection via
 * Lombok, named parameters, an explicit {@link RowMapper}, and generated keys surfaced from the
 * insert.
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

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final ChangeRequestTableColumns T = ChangeRequestTableColumns.of();

  public ChangeRequestRepository(NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  public long insert(ChangeRequestEntity row) {
    Objects.requireNonNull(row, "row");
    var params = new MapSqlParameterSource()
            .addValue("definitionName", row.definitionName())
            .addValue("draftContent", new SqlParameterValue(Types.OTHER, row.draftContent()))
            .addValue("requestedBy", row.requestedBy())
            .addValue("requestedAt", Timestamp.from(row.requestedAt()))
            .addValue("status", row.status().name())
            .addValue("approvedBy", row.approvedBy())
            .addValue("approvedAt",
                    row.approvedAt() == null ? null : Timestamp.from(row.approvedAt()))
            .addValue("comment", row.comment());
    KeyHolder keyHolder = new GeneratedKeyHolder();
    jdbcTemplate.update("""
            INSERT INTO dsl_change_request
                    (definition_name, draft_content, requested_by, requested_at, status,
                     approved_by, approved_at, comment)
            VALUES (:definitionName, :draftContent, :requestedBy, :requestedAt, :status,
                    :approvedBy, :approvedAt, :comment)
            """, params, keyHolder, new String[]{"id"});
    Number key = keyHolder.getKey();
    if (key == null) {
      throw new IllegalStateException(
              "Insert into dsl_change_request returned no generated key");
    }
    return key.longValue();
  }

  public Optional<ChangeRequestEntity> findById(long id) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
            .where(ChangeRequestQueryCriteria.matchesId(T, r, id))
            .build();
    List<ChangeRequestEntity> items = dslQueries.query(query, ROW_MAPPER);
    return items.stream().findFirst();
  }

  public List<ChangeRequestEntity> findByDefinitionName(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
            .where(ChangeRequestQueryCriteria.matchesDefinitionName(T, r, definitionName))
            .orderByDesc(r.get(T.requestedAt()))
            .orderByDesc(r.get(T.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public Optional<ChangeRequestEntity> findPendingByDefinitionName(String definitionName) {
    var r = T.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .select(ChangeRequestQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
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
            .select(ChangeRequestQueryCriteria.fullSelection(T, r)
                    .toArray(new com.github.squigglesql.squigglesql.Selectable[0]))
            .orderByDesc(r.get(T.requestedAt()))
            .orderByDesc(r.get(T.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  public void updateStatus(long id, Status status, @Nullable String approvedBy,
          @Nullable Instant approvedAt, @Nullable String comment) {
    var params = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("status", status.name())
            .addValue("approvedBy", approvedBy)
            .addValue("approvedAt", approvedAt == null ? null : Timestamp.from(approvedAt))
            .addValue("comment", comment);
    jdbcTemplate.update("""
            UPDATE dsl_change_request
            SET status = :status, approved_by = :approvedBy, approved_at = :approvedAt,
                comment = :comment
            WHERE id = :id
            """, params);
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
