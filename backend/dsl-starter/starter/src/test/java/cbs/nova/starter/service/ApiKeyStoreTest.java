package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.persistence.ExtendedSelectQueryExecutor;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import tools.jackson.databind.ObjectMapper;

/**
 * Behavioural tests for {@link ApiKeyStore}: generation round-trip, hash-based match, revoke-then-
 * reject, concurrent valid keys, and best-effort {@code last_used_at} updates that never throw.
 */
class ApiKeyStoreTest {

  private JdbcApiKeyRepository repository;
  private ApiKeyStore store;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:api-key-store-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    NamedParameterJdbcTemplate jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);
    ExtendedSelectQueryExecutor dslQueries = new ExtendedSelectQueryExecutor(jdbcTemplate);
    repository = new JdbcApiKeyRepository(jdbcTemplate, dslQueries);
    store = new ApiKeyStore(repository, new ObjectMapper(), new SelfProvider(storeOrNull()));
  }

  @Test
  void createReturnsPlaintextOnceAndStoresItsHash() {
    ApiKeyStore.CreatedKey created = store.create("bootstrap");

    assertThat(created.plaintext()).isNotBlank();
    assertThat(created.plaintext().length()).isEqualTo(43);
    assertThat(created.prefix()).isEqualTo(created.plaintext().substring(0, 8));
    assertThat(created.label()).isEqualTo("bootstrap");
    assertThat(created.id()).isNotNull();

    String storedHash = repository.findActiveByHash(ApiKeyStore.sha256Hex(created.plaintext()))
            .orElseThrow().keyHash();
    assertThat(storedHash).isEqualTo(ApiKeyStore.sha256Hex(created.plaintext()));
    assertThat(storedHash).hasSize(64);
  }

  @Test
  void matchesReturnsPresentForActiveKeyAndEmptyAfterRevoke() {
    ApiKeyStore.CreatedKey created = store.create("rotating");
    Optional<ApiKeyStore.StoredKeyMatch> match = store.matches(created.plaintext());
    assertThat(match).isPresent();
    assertThat(match.get().label()).isEqualTo("rotating");

    store.revoke(created.id());
    assertThat(store.matches(created.plaintext())).isEmpty();
  }

  @Test
  void matchesReturnsEmptyForUnknownPlaintext() {
    assertThat(store.matches("nope-this-is-not-a-real-key")).isEmpty();
  }

  @Test
  void matchesReturnsEmptyForBlankPlaintext() {
    assertThat(store.matches(null)).isEmpty();
    assertThat(store.matches("")).isEmpty();
    assertThat(store.matches("   ")).isEmpty();
  }

  @Test
  void twoKeysIssuedConcurrentlyBothAuthenticate() {
    ApiKeyStore.CreatedKey a = store.create("a");
    ApiKeyStore.CreatedKey b = store.create("b");

    assertThat(store.matches(a.plaintext())).isPresent();
    assertThat(store.matches(b.plaintext())).isPresent();
    assertThat(store.countActive()).isEqualTo(2L);
  }

  @Test
  void revokeIsIdempotentAndReturnsFalseOnSecondCall() {
    ApiKeyStore.CreatedKey created = store.create("once");
    assertThat(store.revoke(created.id())).isTrue();
    assertThat(store.revoke(created.id())).isFalse();
  }

  @Test
  void revokeReturnsFalseForUnknownId() {
    assertThat(store.revoke(123_456L)).isFalse();
  }

  @Test
  void createRejectsBlankLabel() {
    assertThatThrownBy(() -> store.create(""))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.create(null))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> store.create("   "))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void listExposesLabelPrefixAndTimestampsButNeverTheHashOrPlaintext() {
    ApiKeyStore.CreatedKey created = store.create("for-list");
    String plaintext = created.plaintext();
    String storedHash = ApiKeyStore.sha256Hex(plaintext);

    List<ApiKeyStore.ApiKeyView> rows = store.list();

    assertThat(rows).hasSize(1);
    ApiKeyStore.ApiKeyView row = rows.get(0);
    assertThat(row.label()).isEqualTo("for-list");
    assertThat(row.prefix()).isEqualTo(created.prefix());
    assertThat(row.createdAt()).isNotNull();
    assertThat(row.revokedAt()).isNull();
    assertThat(row.lastUsedAt()).isNull();
    String serialized = row.toString();
    assertThat(serialized).doesNotContain(plaintext);
    assertThat(serialized).doesNotContain(storedHash);
  }

  @Test
  void touchLastUsedSwallowsRepositoryFailure() {
    ApiKeyStore.CreatedKey created = store.create("touch");
    // Replace the repository with a stub that always throws. Store must still complete without
    // surfacing the exception to the caller.
    JdbcApiKeyRepository throwingRepo = new JdbcApiKeyRepository(null, null) {
      @Override
      public void touchLastUsed(long id, Instant lastUsedAt) {
        throw new IllegalStateException("boom");
      }
    };
    ApiKeyStore fragile = new ApiKeyStore(throwingRepo, new ObjectMapper(),
            new SelfProvider(null));

    // No exception: best-effort.
    fragile.touchLastUsed(created.id());

    // Sanity: the real repo can still be updated via touchLastUsed (sanity for the happy path).
    store.touchLastUsed(created.id());
    assertThat(repository.findActiveByHash(ApiKeyStore.sha256Hex(created.plaintext()))
            .orElseThrow().lastUsedAt()).isNotNull();
  }

  private static ApiKeyStore storeOrNull() {
    return null;
  }

  /**
   * Test-only {@link ObjectProvider} that returns a pre-built bean or {@code null} when the store
   * is being constructed (chicken-and-egg: store's constructor takes the provider that resolves to
   * itself).
   */
  private record SelfProvider(ApiKeyStore bean) implements ObjectProvider<ApiKeyStore> {

    @Override
    public ApiKeyStore getIfAvailable() {
      return bean;
    }

    @Override
    public ApiKeyStore getIfUnique() {
      return bean;
    }

    @Override
    public ApiKeyStore getObject() {
      return bean;
    }
  }
}
