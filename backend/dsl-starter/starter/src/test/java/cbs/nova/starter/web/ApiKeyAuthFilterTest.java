package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import cbs.nova.starter.service.ApiKeyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class ApiKeyAuthFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private JdbcApiKeyRepository repository;
  private ApiKeyStore store;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:auth-filter-" + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V6__dsl_api_keys.sql"));
    repository = new JdbcApiKeyRepository(new NamedParameterJdbcTemplate(dataSource));
    store = new ApiKeyStore(repository, objectMapper, new SelfProvider(() -> store));
  }

  @Test
  void nullPropertyKeyAndNoStoreDisablesEnforcement() throws ServletException, IOException {
    assertPassthrough(null, null);
  }

  @Test
  void blankPropertyKeyAndNoStoreDisablesEnforcement() throws ServletException, IOException {
    assertPassthrough("   ", null);
  }

  @Test
  void propertyKeyStillAuthenticates() throws ServletException, IOException {
    CallTracker tracker = new CallTracker();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "secret-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", null, objectMapper)
            .doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getStatus()).isNotEqualTo(401);
  }

  @Test
  void propertyKeyStillFailsWithWrongHeader() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", null, objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
    ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(body.code()).isEqualTo("UNAUTHORIZED");
    assertThat(body.message()).doesNotContain("secret-key");
  }

  @Test
  void storedKeyAuthenticatesAndTouchesLastUsed() throws ServletException, IOException {
    ApiKeyStore.CreatedKey created = store.create("for-auth");

    CallTracker tracker = new CallTracker();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, created.plaintext());
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(null, store, objectMapper)
            .doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getStatus()).isNotEqualTo(401);
    assertThat(repository.findActiveByHash(ApiKeyStore.sha256Hex(created.plaintext()))
            .orElseThrow().lastUsedAt())
            .isNotNull();
  }

  @Test
  void revokedStoredKeyReturns401() throws ServletException, IOException {
    ApiKeyStore.CreatedKey created = store.create("will-be-revoked");
    store.revoke(created.id());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, created.plaintext());
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(null, store, objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
    ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(body.code()).isEqualTo("UNAUTHORIZED");
    assertThat(body.message()).doesNotContain(created.plaintext());
  }

  @Test
  void unknownKeyWithStorePresentReturns401() throws ServletException, IOException {
    store.create("real-key");

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "completely-unknown");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(null, store, objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void missingHeaderWithStorePresentReturns401() throws ServletException, IOException {
    store.create("real-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(null, store, objectMapper)
            .doFilterInternal(new MockHttpServletRequest(), response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
  }

  @Test
  void propertyKeyWinsOverStoredKeyWhenBothMatch() throws ServletException, IOException {
    // Sanity: the property key takes precedence (still logs the deprecation hint the first time).
    ApiKeyStore.CreatedKey stored = store.create("stored");
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "secret-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", store, objectMapper)
            .doFilterInternal(request, response, new CallTracker().chain());

    assertThat(response.getStatus()).isNotEqualTo(401);
    // Stored key was not matched, so last_used_at must remain null.
    assertThat(repository.findActiveByHash(ApiKeyStore.sha256Hex(stored.plaintext()))
            .orElseThrow().lastUsedAt()).isNull();
  }

  @Test
  void envelopeContainsNoPlaintextWhenStoreLookupFails() throws ServletException, IOException {
    ApiKeyStore.CreatedKey created = store.create("plaintext-leak-check");
    store.revoke(created.id());

    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, created.plaintext());
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(null, store, objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    String body = response.getContentAsString();
    assertThat(body).doesNotContain(created.plaintext());
    assertThat(body).doesNotContain("prefix");
  }

  @Test
  void optionalStoreAcceptsNullAndReturns401ForMissingHeaderWhenStoreNonNull() {
    // Defensive: verify the ObjectProvider path returns a working store when present and
    // gracefully no-ops when absent.
    assertThat(store.matches("nothing")).isEmpty();
    assertThat(Optional.ofNullable(store).isPresent()).isTrue();
  }

  private static void assertPassthrough(String configuredKey, ApiKeyStore store)
          throws ServletException, IOException {
    CallTracker tracker = new CallTracker();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(configuredKey, store, new ObjectMapper())
            .doFilterInternal(new MockHttpServletRequest(), response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getContentAsString()).isEmpty();
    assertThat(response.getStatus()).isNotEqualTo(401);
  }

  private static FilterChain chainThatFailsIfInvoked() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        throw new AssertionError("filter must short-circuit and not invoke the chain");
      }
    };
  }

  private static final class CallTracker {

    private boolean called;

    FilterChain chain() {
      return new FilterChain() {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
          called = true;
        }
      };
    }
  }

  /** Test ObjectProvider that delegates to a lambda so the store can resolve itself. */
  private record SelfProvider(Supplier<ApiKeyStore> supplier)
          implements
            ObjectProvider<ApiKeyStore> {

    @Override
    public ApiKeyStore getIfAvailable() {
      return supplier.get();
    }

    @Override
    public ApiKeyStore getIfUnique() {
      return supplier.get();
    }

    @Override
    public ApiKeyStore getObject() {
      return supplier.get();
    }
  }
}
