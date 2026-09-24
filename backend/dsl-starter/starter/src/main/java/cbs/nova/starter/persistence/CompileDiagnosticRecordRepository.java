package cbs.nova.starter.persistence;

import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_CODE_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_DEFINITION_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_FILE_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_SEVERITY_MAX_LENGTH;
import static cbs.nova.starter.core.StarterConstants.COMPILE_DIAGNOSTIC_SOURCE_MAX_LENGTH;

import cbs.nova.dsl.model.CompileDiagnostic;
import cbs.nova.starter.model.CompileDiagnosticRecord;
import cbs.nova.starter.model.CompileDiagnosticSource;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;

/**
 * JDBC access to the append-only {@code dsl_compile_diagnostics} table.
 *
 * <p>
 * Deliberately exposes an insert and a paged query only — the diagnostic log is append-only by
 * convention, so there are no update or delete methods anywhere in the codebase.
 *
 * <p>
 * Follows the {@link cbs.nova.starter.webhook.WebhookDeliveryRecordRepository} idioms: writes flow
 * through the Spring Data {@link CompileDiagnosticCrudRepository}, reads via squigglesql, and an
 * explicit {@link RowMapper}.
 */
public class CompileDiagnosticRecordRepository {

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

  private final CompileDiagnosticCrudRepository crud;
  private final ExtendedSelectQueryExecutor dslQueries;
  private static final CompileDiagnosticTableColumns T = CompileDiagnosticTableColumns.of();

  public CompileDiagnosticRecordRepository(CompileDiagnosticCrudRepository crud,
          ExtendedSelectQueryExecutor dslQueries) {
    this.crud = Objects.requireNonNull(crud);
    this.dslQueries = Objects.requireNonNull(dslQueries);
  }

  /**
   * Appends one diagnostic row. The {@code id} and {@code occurredAt} on the given record are used
   * as supplied; callers normally pass a null identity and {@code Instant.now()}.
   */
  public void insert(CompileDiagnosticRecord row) {
    crud.save(new CompileDiagnosticRecord(
            row.id(),
            row.occurredAt(),
            truncate(row.source(), COMPILE_DIAGNOSTIC_SOURCE_MAX_LENGTH),
            truncate(row.definition(), COMPILE_DIAGNOSTIC_DEFINITION_MAX_LENGTH),
            truncate(row.file(), COMPILE_DIAGNOSTIC_FILE_MAX_LENGTH),
            row.line(),
            row.column(),
            truncate(row.severity(), COMPILE_DIAGNOSTIC_SEVERITY_MAX_LENGTH),
            truncate(row.code(), COMPILE_DIAGNOSTIC_CODE_MAX_LENGTH),
            row.message()));
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

    var r = T.refer();
    boolean hasDefinition = definition != null && !definition.isBlank();

    ExtendedSelectQuery countQuery = dslQueries.select()
            .from(r)
            .whereIf(hasDefinition,
                    () -> Criteria.equal(r.get(T.definition()), Literal.of(definition)))
            .selectCount()
            .build();
    long total = dslQueries.queryForObject(countQuery, Long.class);

    ExtendedSelectQuery dataQuery = dslQueries.select()
            .from(r)
            .whereIf(hasDefinition,
                    () -> Criteria.equal(r.get(T.definition()), Literal.of(definition)))
            .select(r.get(T.id()), r.get(T.occurredAt()), r.get(T.source()),
                    r.get(T.definition()), r.get(T.file()), r.get(T.line()),
                    r.get(T.colNumber()), r.get(T.severity()), r.get(T.code()),
                    r.get(T.message()))
            .orderByDesc(r.get(T.occurredAt()))
            .orderByDesc(r.get(T.id()))
            .limit(limit)
            .offset(offset)
            .build();
    List<CompileDiagnosticRecord> items = dslQueries.query(dataQuery, ROW_MAPPER);
    return new CompileDiagnosticSearchResult(items, total);
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
