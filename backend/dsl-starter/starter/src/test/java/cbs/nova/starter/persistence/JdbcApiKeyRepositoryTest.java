package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.entity.DslApiKeyEntity;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.assertj.core.data.TemporalUnitWithinOffset;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * Live H2 tests for {@link JdbcApiKeyRepository} over the V6 migration. Pins the contract that the
 * unique index on {@code key_hash} is enforced, that {@code findActiveByHash} ignores revoked rows,
 * that {@code markRevoked} is idempotent and returns {@code false} on already-revoked or unknown
 * ids, and that {@code listAll} returns newest-first.
 */
class JdbcApiKeyRepositoryTest {

  private NamedParameterJdbcTemplate jdbcTemplate;
  private JdbcApiKeyRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:api-keys-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    repository = new JdbcApiKeyRepository(jdbcTemplate);
  }

  @Test
  void insertThenFindByHashRoundTripsRow() {
    DslApiKeyEntity row = row("ops-token", "0123456789abcdef0123456789abcdef"
            + "0123456789abcdef0123456789abcdef", Instant.now());
    repository.insert(row);

    Optional<DslApiKeyEntity> found = repository.findActiveByHash(row.keyHash());

    assertThat(found).isPresent();
    assertThat(found.get().label()).isEqualTo("ops-token");
    assertThat(found.get().keyPrefix()).isEqualTo(row.keyPrefix());
    assertThat(found.get().createdAt()).isNotNull();
    assertThat(found.get().revokedAt()).isNull();
    assertThat(found.get().lastUsedAt()).isNull();
  }

  @Test
  void uniqueKeyHashIsEnforced() {
    DslApiKeyEntity row = row("first", "shared-hash-00000000000000000000000000000000",
            Instant.now());
    repository.insert(row);

    DslApiKeyEntity duplicate = row("second", "shared-hash-00000000000000000000000000000000",
            Instant.now());
    Assertions.assertThatThrownBy(() -> repository.insert(duplicate))
            .isInstanceOf(DuplicateKeyException.class);
  }

  @Test
  void findActiveByHashIgnoresRevokedRows() {
    DslApiKeyEntity row = row("to-revoke",
            "abcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd",
            Instant.now());
    repository.insert(row);
    repository.markRevoked(row.id() != null ? row.id() : 1L, Instant.now());

    Optional<DslApiKeyEntity> found = repository.findActiveByHash(row.keyHash());

    assertThat(found).isEmpty();
  }

  @Test
  void findActiveByHashReturnsEmptyForUnknownHash() {
    Optional<DslApiKeyEntity> found = repository.findActiveByHash(
            "0000000000000000000000000000000000000000000000000000000000000000");

    assertThat(found).isEmpty();
  }

  @Test
  void markRevokedReturnsTrueOnceThenFalse() {
    DslApiKeyEntity row = row("revoke-me",
            "1111111111111111111111111111111111111111111111111111111111111111",
            Instant.now());
    repository.insert(row);
    long id = repository.findActiveByHash(row.keyHash()).orElseThrow().id();

    assertThat(repository.markRevoked(id, Instant.now())).isTrue();
    assertThat(repository.markRevoked(id, Instant.now()))
            .as("second revoke must be a no-op")
            .isFalse();
  }

  @Test
  void markRevokedReturnsFalseForUnknownId() {
    assertThat(repository.markRevoked(999_999L, Instant.now())).isFalse();
  }

  @Test
  void touchLastUsedSetsTimestamp() {
    DslApiKeyEntity row = row("touched",
            "2222222222222222222222222222222222222222222222222222222222222222",
            Instant.now());
    repository.insert(row);
    long id = repository.findActiveByHash(row.keyHash()).orElseThrow().id();
    Instant when = Instant.now();

    repository.touchLastUsed(id, when);

    DslApiKeyEntity reloaded = repository.findActiveByHash(row.keyHash()).orElseThrow();
    assertThat(reloaded.lastUsedAt()).isNotNull();
    assertThat(reloaded.lastUsedAt()).isCloseTo(when,
            new TemporalUnitWithinOffset(
                    2_000_000_000L, ChronoUnit.NANOS));
  }

  @Test
  void listAllReturnsRowsNewestFirstAndIncludesRevoked() {
    Instant t0 = Instant.now();
    repository
            .insert(row("first", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                    t0));
    repository.insert(
            row("second", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                    t0.plusSeconds(1)));
    long firstId = repository.findActiveByHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").orElseThrow().id();
    repository.markRevoked(firstId, t0.plusSeconds(2));

    List<DslApiKeyEntity> rows = repository.listAll();

    assertThat(rows).hasSize(2);
    assertThat(rows.get(0).label()).isEqualTo("second");
    assertThat(rows.get(1).label()).isEqualTo("first");
    assertThat(rows.get(1).revokedAt()).isNotNull();
  }

  @Test
  void countActiveExcludesRevokedRows() {
    Instant t0 = Instant.now();
    repository.insert(row("a", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            t0));
    repository.insert(row("b", "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            t0));
    long aId = repository.findActiveByHash(
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa").orElseThrow().id();
    repository.markRevoked(aId, t0);

    assertThat(repository.countActive()).isEqualTo(1L);
  }

  private static DslApiKeyEntity row(String label, String keyHash, Instant now) {
    return new DslApiKeyEntity(null, label, keyHash,
            label.substring(0, Math.min(8, label.length())),
            now, null, null);
  }
}
