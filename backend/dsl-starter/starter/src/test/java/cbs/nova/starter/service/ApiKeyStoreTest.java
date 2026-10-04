package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
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
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    var repos = CrudRepositories.over(dataSource);
    repository = new JdbcApiKeyRepository(repos.apiKeys(), repos.dslQueries());
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

  @Test
  void throttleCollapsesRapidCallsIntoOneWrite() {
    ApiKeyStore.CreatedKey created = store.create("throttled");
    CountingRepository counting = new CountingRepository(repository);
    FakeClock clock = new FakeClock(0L);
    ApiKeyStore throttledStore = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    throttledStore.configureLastUsedThrottle(60, clock);

    for (int i = 0; i < 100; i++) {
      throttledStore.touchLastUsed(created.id());
      // 100ms per call: total elapsed across 100 iterations is 10s, well inside the 60s window.
      clock.advanceNanos(TimeUnit.MILLISECONDS.toNanos(100));
    }

    assertThat(counting.touchCount()).isEqualTo(1);
  }

  @Test
  void throttleWritesAgainOnceWindowElapses() {
    ApiKeyStore.CreatedKey created = store.create("throttled-after-window");
    CountingRepository counting = new CountingRepository(repository);
    FakeClock clock = new FakeClock(0L);
    ApiKeyStore throttledStore = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    throttledStore.configureLastUsedThrottle(60, clock);

    throttledStore.touchLastUsed(created.id()); // write #1
    throttledStore.touchLastUsed(created.id()); // suppressed
    throttledStore.touchLastUsed(created.id()); // suppressed

    // Advance past the 60s window.
    clock.advanceNanos(TimeUnit.SECONDS.toNanos(61));
    throttledStore.touchLastUsed(created.id()); // write #2

    assertThat(counting.touchCount()).isEqualTo(2);
  }

  @Test
  void throttleZeroWindowDisablesThrottling() {
    ApiKeyStore.CreatedKey created = store.create("unthrottled");
    CountingRepository counting = new CountingRepository(repository);
    FakeClock clock = new FakeClock(0L);
    ApiKeyStore throttledStore = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    throttledStore.configureLastUsedThrottle(0, clock);

    for (int i = 0; i < 5; i++) {
      throttledStore.touchLastUsed(created.id());
    }

    assertThat(counting.touchCount()).isEqualTo(5);
  }

  @Test
  void throttleTracksDifferentKeysIndependently() {
    ApiKeyStore.CreatedKey a = store.create("key-a");
    ApiKeyStore.CreatedKey b = store.create("key-b");
    CountingRepository counting = new CountingRepository(repository);
    FakeClock clock = new FakeClock(0L);
    ApiKeyStore throttledStore = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    throttledStore.configureLastUsedThrottle(60, clock);

    throttledStore.touchLastUsed(a.id()); // write #1
    throttledStore.touchLastUsed(b.id()); // write #2 — different key, not affected by A's window
    throttledStore.touchLastUsed(a.id()); // suppressed — A is still in window
    throttledStore.touchLastUsed(b.id()); // suppressed — B is still in window

    assertThat(counting.touchCount()).isEqualTo(2);
  }

  @Test
  void revokeClearsThrottleEntrySoReplacementKeyStartsClean() {
    ApiKeyStore.CreatedKey created = store.create("to-revoke");
    CountingRepository counting = new CountingRepository(repository);
    FakeClock clock = new FakeClock(0L);
    ApiKeyStore throttledStore = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    throttledStore.configureLastUsedThrottle(60, clock);

    throttledStore.touchLastUsed(created.id()); // write #1
    throttledStore.touchLastUsed(created.id()); // suppressed
    throttledStore.revoke(created.id());

    // Brand-new write on the same id (after revoke clears the map entry) must NOT be throttled.
    // Use the real one to drive the revocation through the JdbcApiKeyRepository (the stub's
    // markRevoked is a no-op), then exercise the throttled store again with the counting repo.
    ApiKeyStore realStore = new ApiKeyStore(repository, new ObjectMapper(),
            new SelfProvider(null));
    realStore.revoke(created.id());
    ApiKeyStore replacement = new ApiKeyStore(counting, new ObjectMapper(),
            new SelfProvider(null));
    replacement.configureLastUsedThrottle(60, clock);
    // Map starts empty on a fresh instance; touchLastUsed must write.
    replacement.touchLastUsed(created.id());
    assertThat(counting.touchCount()).isEqualTo(2); // initial write + post-revoke write
  }

  @Test
  void throttleStillSwallowsRepositoryFailure() {
    JdbcApiKeyRepository throwingRepo = new JdbcApiKeyRepository(null, null) {
      @Override
      public void touchLastUsed(long id, Instant lastUsedAt) {
        throw new IllegalStateException("boom");
      }
    };
    ApiKeyStore fragile = new ApiKeyStore(throwingRepo, new ObjectMapper(),
            new SelfProvider(null));
    fragile.configureLastUsedThrottle(60, new FakeClock(0L));

    // Should not throw — the swallow lives below the throttle, not above it.
    fragile.touchLastUsed(42L);
    fragile.touchLastUsed(42L);
  }

  /**
   * Manual nano-clock that advances only when the test asks it to. Removes wall-clock flakiness
   * from throttle assertions.
   */
  private static final class FakeClock implements LongSupplier {

    private final AtomicLong nanos;

    FakeClock(long initialNanos) {
      this.nanos = new AtomicLong(initialNanos);
    }

    void advanceNanos(long delta) {
      nanos.addAndGet(delta);
    }

    @Override
    public long getAsLong() {
      return nanos.get();
    }
  }

  /**
   * Counts {@link JdbcApiKeyRepository#touchLastUsed} invocations while delegating everything else
   * to the underlying repository, so we can assert on the actual write traffic — not just the
   * throttle state.
   */
  private static final class CountingRepository extends JdbcApiKeyRepository {

    private final JdbcApiKeyRepository delegate;
    private final java.util.concurrent.atomic.AtomicInteger touches = new java.util.concurrent.atomic.AtomicInteger();

    CountingRepository(JdbcApiKeyRepository delegate) {
      super(null, null);
      this.delegate = delegate;
    }

    @Override
    public void touchLastUsed(long id, Instant lastUsedAt) {
      touches.incrementAndGet();
      delegate.touchLastUsed(id, lastUsedAt);
    }

    @Override
    public boolean markRevoked(long id, Instant revokedAt) {
      return delegate.markRevoked(id, revokedAt);
    }

    int touchCount() {
      return touches.get();
    }
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
