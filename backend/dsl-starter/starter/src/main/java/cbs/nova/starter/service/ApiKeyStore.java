package cbs.nova.starter.service;

import cbs.nova.starter.entity.DslApiKeyEntity;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Stored API-key store for the rotatable-key surface (T410).
 *
 * <p>
 * <b>Key generation.</b> 32 bytes from {@link SecureRandom} are encoded with the URL-safe base64
 * alphabet ({@link Base64#getUrlEncoder()}) and emitted without padding — 43 ASCII chars giving 256
 * bits of entropy. The plaintext is returned exactly once from {@link #create(String)}; it is never
 * written to disk, never logged, never echoed back from any read path.
 *
 * <p>
 * <b>Hashing.</b> The plaintext is hashed with SHA-256 and the lowercase hex digest is what
 * persists in {@code dsl_api_keys.key_hash}. Equality is enforced by the
 * {@code uq_dsl_api_keys_key_hash} unique index — the database is the constant-time comparison.
 * There is no plaintext-vs-stored compare anywhere in the codebase.
 *
 * <p>
 * <b>{@code last_used_at}.</b> Touched best-effort after a successful match. The
 * {@link JdbcApiKeyRepository#touchLastUsed(long, Instant)} call is wrapped so a JDBC failure here
 * can never fail authentication.
 *
 * <p>
 * <b>Self-injection.</b> {@code touchLastUsed} runs on the auth-filter hot path. We re-resolve the
 * proxy through the application context so a request-scoped / proxied bean still injects its
 * dependencies correctly. The {@code @Lazy} flag prevents eager construction of the filter-side
 * dependency chain at boot.
 */
@Service
public final class ApiKeyStore {

  private static final Logger LOG = LoggerFactory.getLogger(ApiKeyStore.class);

  /** Number of random bytes — 32 bytes = 256 bits of entropy. */
  private static final int RANDOM_BYTES = 32;
  /** Length of the visible identifier stored alongside the hash (and returned by list views). */
  private static final int PREFIX_LENGTH = 8;

  private final JdbcApiKeyRepository repository;
  private final ObjectMapper objectMapper;
  /** Non-final so tests can swap in a deterministic source. Package-private setter below. */
  private SecureRandom random = new SecureRandom();
  /**
   * Self-reference resolved via the application context. Required because the auth filter calls
   * {@link #touchLastUsed(long)} from its own thread and we want the same proxied bean (e.g. for
   * {@code @Transactional}) to handle it, not a freshly-constructed unproxied instance.
   */
  private final ObjectProvider<ApiKeyStore> selfProvider;

  @Autowired
  public ApiKeyStore(JdbcApiKeyRepository repository,
          ObjectMapper objectMapper,
          @Lazy ObjectProvider<ApiKeyStore> selfProvider) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.selfProvider = selfProvider;
  }

  /**
   * Result of a successful lookup — tells the auth filter which row matched so it can update
   * {@code last_used_at}. The {@code keyHash} and plaintext are intentionally not part of this
   * record: callers MUST NOT see them after the original creation.
   */
  public record StoredKeyMatch(long id, String label) {
  }

  /**
   * Returned by {@link #create(String)} — the plaintext is included exactly once so the caller can
   * hand it to a human operator. There is no other path back to the plaintext from the database.
   */
  public record CreatedKey(long id, String label, String prefix, String plaintext) {
  }

  /** Projection used by the list endpoint — never exposes the hash or plaintext. */
  public record ApiKeyView(long id, String label, String prefix, Instant createdAt,
          Instant revokedAt, Instant lastUsedAt) {

    public static ApiKeyView from(DslApiKeyEntity entity) {
      return new ApiKeyView(entity.id(), entity.label(), entity.keyPrefix(),
              entity.createdAt(), entity.revokedAt(), entity.lastUsedAt());
    }
  }

  /**
   * Looks up a presented plaintext by hashing it and looking up the SHA-256 hex digest among
   * non-revoked rows. Returns empty when the key is unknown or revoked. Never logs the plaintext.
   */
  public Optional<StoredKeyMatch> matches(String plaintext) {
    if (plaintext == null || plaintext.isBlank()) {
      return Optional.empty();
    }
    String hash = sha256Hex(plaintext);
    return repository.findActiveByHash(hash)
            .map(row -> new StoredKeyMatch(row.id(), row.label()));
  }

  /**
   * Counts active (non-revoked) rows. The auth filter uses this to decide whether auth is required
   * when no {@code cbs.dsl.auth.api-key} property is set.
   */
  public long countActive() {
    return repository.countActive();
  }

  /**
   * Generates a fresh 256-bit random key, stores its SHA-256 hash + label + visible prefix, and
   * returns the plaintext to the caller. The plaintext is recoverable only via this return value.
   */
  public CreatedKey create(String label) {
    if (label == null || label.isBlank()) {
      throw new IllegalArgumentException("label must not be blank");
    }
    byte[] bytes = new byte[RANDOM_BYTES];
    random.nextBytes(bytes);
    String plaintext = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String prefix = plaintext.substring(0, Math.min(PREFIX_LENGTH, plaintext.length()));
    String hash = sha256Hex(plaintext);
    Instant now = Instant.now();
    DslApiKeyEntity row = new DslApiKeyEntity(null, label, hash, prefix, now, null, null);
    repository.insert(row);
    LOG.info("created API key label={} prefix={}", label, prefix);
    // id is generated by the DB; we expose it via a follow-up read of the most-recently-created
    // row for this hash (unique-indexed) so the caller can revoke it later.
    DslApiKeyEntity persisted = repository.findActiveByHash(hash)
            .orElseThrow(() -> new IllegalStateException(
                    "insert succeeded but row is no longer visible: label=" + label));
    return new CreatedKey(persisted.id(), label, prefix, plaintext);
  }

  /** Revokes a stored key by id. Idempotent for already-revoked ids (returns false). */
  public boolean revoke(long id) {
    boolean changed = repository.markRevoked(id, Instant.now());
    if (changed) {
      LOG.info("revoked API key id={}", id);
    }
    return changed;
  }

  /** Lists every stored key for the admin endpoint. NEVER includes the hash or plaintext. */
  public List<ApiKeyView> list() {
    return repository.listAll().stream()
            .map(ApiKeyView::from)
            .toList();
  }

  /**
   * Best-effort update of {@code last_used_at}. Failures are logged at WARN and swallowed — the
   * auth filter MUST NOT fail authentication because of a bookkeeping update.
   */
  public void touchLastUsed(long id) {
    try {
      // Re-resolve through the proxy so any transactional / scoped behaviour applies on this
      // thread too. If no proxy is configured (e.g. unit tests wire the bean directly), fall
      // back to {@code this}.
      ApiKeyStore proxy = selfProvider.getIfAvailable();
      (proxy != null ? proxy : this).repository.touchLastUsed(id, Instant.now());
    } catch (RuntimeException e) {
      LOG.warn("failed to update last_used_at for api key id={}: {}", id, e.getMessage());
    }
  }

  /**
   * Lowercase hex SHA-256 of the given plaintext. The hash function lives here (not in the
   * repository) because every code path that persists or looks up a key goes through this method,
   * so the algorithm choice is enforced in exactly one place.
   */
  public static String sha256Hex(String plaintext) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] bytes = digest.digest(plaintext.getBytes(StandardCharsets.UTF_8));
      StringBuilder sb = new StringBuilder(bytes.length * 2);
      for (byte b : bytes) {
        sb.append(Character.forDigit((b >> 4) & 0xF, 16));
        sb.append(Character.forDigit(b & 0xF, 16));
      }
      return sb.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available on this JVM", e);
    }
  }

  /**
   * Test seam — lets unit tests substitute a deterministic {@link SecureRandom} source so the
   * generated plaintext is reproducible. Not exposed in any production code path.
   */
  void setRandomForTesting(SecureRandom random) {
    this.random = random;
  }

  /** Test seam — expose the JSON object mapper so handler tests can assert envelope shape. */
  ObjectMapper objectMapperForTest() {
    return objectMapper;
  }
}
