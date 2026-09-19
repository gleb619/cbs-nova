package cbs.nova.starter.persistence;

import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
@RequiredArgsConstructor
public class ChangeRequestRepository {

  private static final String COLUMNS = "id, definition_name, draft_content, requested_by,"
          + " requested_at, status, approved_by, approved_at, comment";

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

  /** Inserts one change request and returns the generated id. */
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
    List<ChangeRequestEntity> items = jdbcTemplate.query(
            "SELECT %s FROM dsl_change_request WHERE id = :id".formatted(COLUMNS),
            new MapSqlParameterSource("id", id), ROW_MAPPER);
    return items.stream().findFirst();
  }

  /** All rows for one definition, newest first. */
  public List<ChangeRequestEntity> findByDefinitionName(String definitionName) {
    return jdbcTemplate.query("""
            SELECT %s FROM dsl_change_request
            WHERE definition_name = :definitionName
            ORDER BY requested_at DESC, id DESC
            """.formatted(COLUMNS), new MapSqlParameterSource("definitionName", definitionName),
            ROW_MAPPER);
  }

  /** The single PENDING row for a definition, if any (at most one exists: submit supersedes). */
  public Optional<ChangeRequestEntity> findPendingByDefinitionName(String definitionName) {
    List<ChangeRequestEntity> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_change_request
            WHERE definition_name = :definitionName AND status = 'PENDING'
            ORDER BY id DESC
            """.formatted(COLUMNS), new MapSqlParameterSource("definitionName", definitionName),
            ROW_MAPPER);
    return items.stream().findFirst();
  }

  /**
   * Filtered listing, newest first. Both filters are optional; {@code null} means "no filter".
   */
  public List<ChangeRequestEntity> findAll(@Nullable String definitionName,
          @Nullable Status status) {
    var params = new MapSqlParameterSource();
    StringBuilder sql = new StringBuilder(
            "SELECT %s FROM dsl_change_request WHERE 1=1".formatted(COLUMNS));
    if (definitionName != null) {
      sql.append(" AND definition_name = :definitionName");
      params.addValue("definitionName", definitionName);
    }
    if (status != null) {
      sql.append(" AND status = :status");
      params.addValue("status", status.name());
    }
    sql.append(" ORDER BY requested_at DESC, id DESC");
    return jdbcTemplate.query(sql.toString(), params, ROW_MAPPER);
  }

  /** Status transition (approve / reject / supersede); the actor/timestamp/comment are nullable. */
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
    Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dsl_change_request",
            new MapSqlParameterSource(), Long.class);
    return total != null ? total : 0;
  }
}
