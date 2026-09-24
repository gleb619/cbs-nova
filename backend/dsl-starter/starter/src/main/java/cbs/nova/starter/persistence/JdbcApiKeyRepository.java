package cbs.nova.starter.persistence;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.DslApiKeyEntity;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * JDBC access to the {@code dsl_api_keys} table (T410).
 *
 * <p>
 * Follows the {@link DslDefinitionTestRepository} idioms: constructor injection, named parameters,
 * and an explicit {@link RowMapper}. The repository only stores and looks up keys by their SHA-256
 * hash; the plaintext key never reaches this layer.
 */
public class JdbcApiKeyRepository {

  private static final RowMapper<DslApiKeyEntity> ROW_MAPPER = (rs, rowNum) -> new DslApiKeyEntity(
          rs.getLong("id"),
          rs.getString("label"),
          rs.getString("key_hash"),
          rs.getString("key_prefix"),
          rs.getTimestamp("created_at").toInstant(),
          rs.getTimestamp("revoked_at") == null ? null : rs.getTimestamp("revoked_at").toInstant(),
          rs.getTimestamp("last_used_at") == null
                  ? null
                  : rs.getTimestamp("last_used_at").toInstant());

  private final NamedParameterJdbcTemplate jdbcTemplate;
  private final ExtendedSelectQueryExecutor dslQueries;

  public JdbcApiKeyRepository(NamedParameterJdbcTemplate jdbcTemplate,
          ExtendedSelectQueryExecutor dslQueries) {
    this.jdbcTemplate = jdbcTemplate;
    this.dslQueries = dslQueries;
  }

  /**
   * Looks up an active (non-revoked) row by its SHA-256 hex digest. The unique index on
   * {@code key_hash} makes this an O(log n) equality check; equality is inherent (no
   * plaintext-vs-stored comparison) so this is also the constant-time check.
   */
  public Optional<DslApiKeyEntity> findActiveByHash(String keyHash) {
    ApiKeyTableColumns t = ApiKeyTableColumns.of();
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .from(r)
            .select(ApiKeyQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .where(ApiKeyQueryCriteria.matchesKeyHash(t, r, keyHash))
            .where(ApiKeyQueryCriteria.isActive(t, r))
            .build();
    List<DslApiKeyEntity> rows = dslQueries.query(query, ROW_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
  }

  /**
   * Counts active (non-revoked) rows. Used by the auth filter to decide whether auth is required
   * even when no {@code cbs.dsl.auth.api-key} property is set.
   */
  public long countActive() {
    ApiKeyTableColumns t = ApiKeyTableColumns.of();
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .from(r)
            .select(Literal.unsafe("COUNT(*)"))
            .where(ApiKeyQueryCriteria.isActive(t, r))
            .build();
    return dslQueries.queryForObject(query, Long.class);
  }

  public void insert(DslApiKeyEntity row) {
    var params = new MapSqlParameterSource()
            .addValue("label", row.label())
            .addValue("keyHash", row.keyHash())
            .addValue("keyPrefix", row.keyPrefix())
            .addValue("createdAt", Timestamp.from(row.createdAt()))
            .addValue("revokedAt", row.revokedAt() == null ? null : Timestamp.from(row.revokedAt()))
            .addValue("lastUsedAt",
                    row.lastUsedAt() == null ? null : Timestamp.from(row.lastUsedAt()));
    jdbcTemplate.update("""
            INSERT INTO dsl_api_keys
                    (label, key_hash, key_prefix, created_at, revoked_at, last_used_at)
            VALUES (:label, :keyHash, :keyPrefix, :createdAt, :revokedAt, :lastUsedAt)
            """, params);
  }

  /**
   * Marks a row as revoked. Returns {@code true} if a non-revoked row with that id was updated,
   * {@code false} when the id is unknown or already revoked — used by the admin endpoint to map to
   * 404.
   */
  public boolean markRevoked(long id, Instant revokedAt) {
    int rows = jdbcTemplate.update("""
            UPDATE dsl_api_keys
            SET revoked_at = :revokedAt
            WHERE id = :id AND revoked_at IS NULL
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("revokedAt", Timestamp.from(revokedAt)));
    return rows > 0;
  }

  /**
   * Best-effort update of {@code last_used_at}; never called from the auth filter on the success
   * path unless the row actually matched. Failures are surfaced to the caller (which the
   * {@code ApiKeyStore} swallows) so the auth hot path stays protected.
   */
  public void touchLastUsed(long id, Instant lastUsedAt) {
    jdbcTemplate.update("""
            UPDATE dsl_api_keys
            SET last_used_at = :lastUsedAt
            WHERE id = :id
            """, new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("lastUsedAt", Timestamp.from(lastUsedAt)));
  }

  public List<DslApiKeyEntity> listAll() {
    ApiKeyTableColumns t = ApiKeyTableColumns.of();
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .from(r)
            .select(ApiKeyQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .orderByDesc(r.get(t.createdAt()))
            .orderByDesc(r.get(t.id()))
            .build();
    return dslQueries.query(query, ROW_MAPPER);
  }

  /**
   * Loads one row by id without filtering on {@code revoked_at} so the admin list can show
   * historical (revoked) entries. Returns empty when the id is unknown.
   */
  public Optional<DslApiKeyEntity> findById(long id) {
    ApiKeyTableColumns t = ApiKeyTableColumns.of();
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .from(r)
            .select(ApiKeyQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .where(ApiKeyQueryCriteria.matchesId(t, r, id))
            .build();
    List<DslApiKeyEntity> rows = dslQueries.query(query, ROW_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
  }

  /**
   * Loads one row by id only if it is currently active. Used as a defensive check before
   * {@link #touchLastUsed} on the auth hot path so a revoked row can never have its
   * {@code last_used_at} touched.
   */
  public Optional<DslApiKeyEntity> findActiveById(long id) {
    ApiKeyTableColumns t = ApiKeyTableColumns.of();
    TableReference r = t.refer();
    ExtendedSelectQuery query = dslQueries.select()
            .from(r)
            .select(ApiKeyQueryCriteria.fullSelection(t, r).toArray(Selectable[]::new))
            .where(ApiKeyQueryCriteria.matchesId(t, r, id))
            .where(ApiKeyQueryCriteria.isActive(t, r))
            .build();
    List<DslApiKeyEntity> rows = dslQueries.query(query, ROW_MAPPER);
    return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
  }

  @SuppressWarnings("unused")
  private static void requireNonNull(@Nullable Object value, String name) {
    if (value == null) {
      throw new IllegalArgumentException(name + " must not be null");
    }
  }
}
