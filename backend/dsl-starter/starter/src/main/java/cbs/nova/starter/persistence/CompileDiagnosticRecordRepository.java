package cbs.nova.starter.persistence;

import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_CODE_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_DEFINITION_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_FILE_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_SEVERITY_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_SOURCE_MAX_LENGTH;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.CompileDiagnosticRecord;
import cbs.nova.starter.model.CompileDiagnosticSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC access to the append-only {@code dsl_compile_diagnostics} table.
 *
 * <p>
 * Deliberately exposes an insert and a paged query only — the diagnostic log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 *
 * <p>
 * Follows the {@link cbs.nova.starter.webhook.WebhookDeliveryRecordRepository} idioms: constructor
 * injection via Lombok, named parameters, and an explicit {@link RowMapper}.
 */
@RequiredArgsConstructor
public class CompileDiagnosticRecordRepository {

  private static final String COLUMNS = "id, occurred_at, source, definition, file, line, col_number, severity, code, message";

  private static final RowMapper<CompileDiagnosticRecord> ROW_MAPPER = (rs,
          rowNum) -> new CompileDiagnosticRecord(
                  rs.getLong("id"),
                  rs.getTimestamp("occurred_at").toInstant(),
                  rs.getString("source"),
                  rs.getString("definition"),
                  rs.getString("file"),
                  longOrNull(rs.getObject("line", Integer.class)),
                  longOrNull(rs.getObject("col_number", Integer.class)),
                  rs.getString("severity"),
                  rs.getString("code"),
                  rs.getString("message"));

  private final NamedParameterJdbcTemplate jdbcTemplate;

  /**
   * Appends one diagnostic row. The {@code id} and {@code occurredAt} on the given record are used
   * as supplied; callers normally pass a null identity and {@code Instant.now()}.
   */
  public void insert(CompileDiagnosticRecord row) {
    var params = new MapSqlParameterSource()
            .addValue("occurredAt", Timestamp.from(row.occurredAt()))
            .addValue("source", truncate(row.source(), COMPILE_DIAGNOSTIC_SOURCE_MAX_LENGTH))
            .addValue("definition",
                    truncate(row.definition(), COMPILE_DIAGNOSTIC_DEFINITION_MAX_LENGTH))
            .addValue("file", truncate(row.file(), COMPILE_DIAGNOSTIC_FILE_MAX_LENGTH))
            .addValue("line", row.line() != null ? row.line().intValue() : null)
            .addValue("colNumber", row.column() != null ? row.column().intValue() : null)
            .addValue("severity", truncate(row.severity(), COMPILE_DIAGNOSTIC_SEVERITY_MAX_LENGTH))
            .addValue("code", truncate(row.code(), COMPILE_DIAGNOSTIC_CODE_MAX_LENGTH))
            .addValue("message", row.message());
    jdbcTemplate.update(
            """
                    INSERT INTO dsl_compile_diagnostics
                            (occurred_at, source, definition, file, line, col_number, severity, code, message)
                    VALUES (:occurredAt, :source, :definition, :file, :line, :colNumber, :severity, :code, :message)
                    """,
            params);
  }

  /**
   * Appends all diagnostics for a single compile event. Each diagnostic is stamped with the given
   * source, definition context, and the current instant.
   */
  public void insertAll(CompileDiagnosticSource source, String definition,
          List<CompileDiagnostic> diagnostics) {
    if (diagnostics == null || diagnostics.isEmpty()) {
      return;
    }
    Instant occurredAt = Instant.now();
    String sourceName = source.name();
    for (CompileDiagnostic diagnostic : diagnostics) {
      insert(new CompileDiagnosticRecord(
              null,
              occurredAt,
              sourceName,
              definition,
              diagnostic.file(),
              diagnostic.line(),
              diagnostic.column(),
              diagnostic.severity(),
              diagnostic.code(),
              diagnostic.message()));
    }
  }

  /**
   * Paged query, newest first. {@code definition}, when non-blank, narrows the result set.
   */
  public CompileDiagnosticSearchResult search(@Nullable String definition, int offset,
          int limit) {
    if (offset < 0) {
      throw new IllegalArgumentException("offset must be non-negative, was " + offset);
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be positive, was " + limit);
    }

    String where = "";
    var params = new MapSqlParameterSource();
    if (definition != null && !definition.isBlank()) {
      where = "WHERE definition = :definition";
      params.addValue("definition", definition);
    }

    Long total = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dsl_compile_diagnostics " + where, params, Long.class);

    params.addValue("limit", limit);
    params.addValue("offset", offset);
    List<CompileDiagnosticRecord> items = jdbcTemplate.query("""
            SELECT %s FROM dsl_compile_diagnostics %s
            ORDER BY occurred_at DESC, id DESC
            LIMIT :limit OFFSET :offset
            """.formatted(COLUMNS, where), params, ROW_MAPPER);
    return new CompileDiagnosticSearchResult(items, total != null ? total : 0L);
  }

  private static @Nullable Long longOrNull(@Nullable Integer value) {
    return value == null ? null : Long.valueOf(value);
  }

  private static @Nullable String truncate(@Nullable String value, int maxLength) {
    if (value == null || value.length() <= maxLength) {
      return value;
    }
    return value.substring(0, maxLength);
  }
}
