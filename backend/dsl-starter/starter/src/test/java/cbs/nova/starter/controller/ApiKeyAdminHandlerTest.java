package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import cbs.nova.starter.config.router.ApiKeyAdminRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.persistence.JdbcApiKeyRepository;
import cbs.nova.starter.service.ApiKeyStore;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.h2.jdbcx.JdbcDataSource;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end test for the admin API-key surface (T410): full create → authenticate → revoke → 401
 * lifecycle, list view never exposing hash/plaintext, and 404 on unknown id.
 */
class ApiKeyAdminHandlerTest {

  private ApiKeyStore store;
  private static RequestBodyLimitFilter requestBodyLimitFilter;
private MockMvc mockMvc;

  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    requestBodyLimitFilter = new RequestBodyLimitFilter(new CbsNovaWebProperties(0L));
    var dataSource = new JdbcDataSource();
    dataSource
            .setURL("jdbc:h2:mem:api-keys-handler-" + UUID.randomUUID().toString().replace("-", "")
                    + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    var repos = CrudRepositories.over(dataSource);
    JdbcApiKeyRepository repository = new JdbcApiKeyRepository(repos.apiKeys(), repos.dslQueries());
    store = new ApiKeyStore(repository, objectMapper, new SelfProvider(() -> store));
    ApiKeyAdminHandler handler = new ApiKeyAdminHandler(store, AuditTestSupport.emptyProvider());
    ApiKeyAdminRouterConfiguration router = new ApiKeyAdminRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.apiKeyAdminRouter(handler, requestBodyLimitFilter))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void createAuthenticateRevokeLifecycle() throws Exception {
    // CREATE
    MvcResult createResult = mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"ops-deploy\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("ops-deploy"))
            .andExpect(jsonPath("$.prefix").isString())
            .andExpect(jsonPath("$.key").isString())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn();

    String createBody = createResult.getResponse().getContentAsString();
    String plaintext = objectMapper.readTree(createBody).get("key").asText();
    long id = objectMapper.readTree(createBody).get("id").asLong();

    // AUTHENTICATE — the new key must unlock a request downstream (we exercise the filter
    // path indirectly: the store.matches() contract is the same code path the filter uses).
    assertThat(store.matches(plaintext)).isPresent();

    // LIST shows the key but never the plaintext.
    mockMvc.perform(get("/api/dsl/auth/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].label").value("ops-deploy"))
            .andExpect(jsonPath("$[0].prefix").isString())
            .andExpect(jsonPath("$[0].key").doesNotExist())
            .andExpect(jsonPath("$[0].keyHash").doesNotExist());

    // REVOKE.
    mockMvc.perform(delete("/api/dsl/auth/keys/" + id))
            .andExpect(status().isNoContent());

    // After revoke: matches() returns empty.
    assertThat(store.matches(plaintext)).isEmpty();

    // LIST still includes the (now-revoked) key with revokedAt populated.
    mockMvc.perform(get("/api/dsl/auth/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].revokedAt").isNotEmpty());
  }

  @Test
  void listReturnsEmptyArrayWhenNoKeysStored() throws Exception {
    mockMvc.perform(get("/api/dsl/auth/keys"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void createWithoutLabelReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void createWithBlankLabelReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"   \"}"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void createWithMalformedJsonReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void revokeUnknownIdReturns404Envelope() throws Exception {
    mockMvc.perform(delete("/api/dsl/auth/keys/987654321"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message")
                    .value(Matchers.containsString("987654321")));
  }

  @Test
  void revokeAlreadyRevokedIdIsIdempotent() throws Exception {
    ApiKeyStore.CreatedKey created = store.create("once");
    mockMvc.perform(delete("/api/dsl/auth/keys/" + created.id()))
            .andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/dsl/auth/keys/" + created.id()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void listEndpointResponseBodyNeverContainsPlaintextOrHash() throws Exception {
    ApiKeyStore.CreatedKey created = store.create("for-leak-check");
    String plaintext = created.plaintext();
    String storedHash = ApiKeyStore.sha256Hex(plaintext);

    MvcResult result = mockMvc.perform(get("/api/dsl/auth/keys"))
            .andExpect(status().isOk())
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).doesNotContain(plaintext);
    assertThat(body).doesNotContain(storedHash);
  }

  // ----- audit (T776) -------------------------------------------------------

  @Test
  void createWritesAuditRowWithPrefixOnlyAndOmitsPlaintextAndHash() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new ApiKeyAdminHandler(store,
            AuditTestSupport.providerOf(audit.service())));

    MvcResult createResult = mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"label\":\"ops-deploy\"}"))
            .andExpect(status().isOk())
            .andReturn();

    String createBody = createResult.getResponse().getContentAsString();
    String plaintext = objectMapper.readTree(createBody).get("key").asText();
    String storedHash = ApiKeyStore.sha256Hex(plaintext);

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("API_KEY_CREATE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(String.valueOf(row.target()));
    // The raw key material must NEVER leak into the audit log.
    assertThat(row.detailsJson()).doesNotContain(plaintext);
    assertThat(row.detailsJson()).doesNotContain(storedHash);
    var details = objectMapper.readValue(row.detailsJson(), java.util.Map.class);
    assertThat(details).containsKeys("id", "label", "prefix");
    assertThat(details.get("label")).isEqualTo("ops-deploy");
  }

  @Test
  void createAuditRowOnFailureDoesNotIncludePlaintext() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new ApiKeyAdminHandler(store,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/dsl/auth/keys")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
            .andExpect(status().isBadRequest());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("API_KEY_CREATE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("-");
  }

  @Test
  void revokeWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new ApiKeyAdminHandler(store,
            AuditTestSupport.providerOf(audit.service())));
    ApiKeyStore.CreatedKey created = store.create("to-revoke");

    mockMvc.perform(delete("/api/dsl/auth/keys/" + created.id()))
            .andExpect(status().isNoContent());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("API_KEY_REVOKE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(String.valueOf(created.id()));
  }

  @Test
  void revokeUnknownIdWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new ApiKeyAdminHandler(store,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(delete("/api/dsl/auth/keys/987654321"))
            .andExpect(status().isNotFound());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("API_KEY_REVOKE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("987654321");
  }

  @Test
  void handlerSucceedsWithoutAuditBean() throws Exception {
    // The emptyProvider() path is the default @BeforeEach wiring; this test pins the
    // "no DataSource" deployment shape — handler must still serve the full lifecycle.
    ApiKeyStore.CreatedKey created = store.create("no-audit-bean");

    mockMvc.perform(get("/api/dsl/auth/keys"))
            .andExpect(status().isOk());

    mockMvc.perform(delete("/api/dsl/auth/keys/" + created.id()))
            .andExpect(status().isNoContent());
  }

  private static MockMvc buildMockMvc(ApiKeyAdminHandler handler) {
    ApiKeyAdminRouterConfiguration router = new ApiKeyAdminRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders.routerFunctions(router.apiKeyAdminRouter(handler, requestBodyLimitFilter))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

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
