package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.router.NotificationRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.NotificationRuleModels.ChannelDto;
import cbs.nova.starter.model.NotificationRuleModels.EventFilterDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationActionDto;
import cbs.nova.starter.model.NotificationRuleModels.NotificationRuleDto;
import cbs.nova.starter.model.NotificationRuleModels.ToggleEnabledRequest;
import cbs.nova.starter.notification.NotificationRuleEngine;
import cbs.nova.starter.notification.NotificationRuleService;
import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.persistence.NotificationRuleFiringRepository;
import cbs.nova.starter.persistence.NotificationRuleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit characterization spec for {@link NotificationRuleHandler}: CRUD envelope shapes, validation
 * errors, and 404 paths on the notification rules admin surface.
 */
class NotificationRuleHandlerTest {

  private MockMvc mockMvc;
  private NotificationRuleService ruleService;
  private NotificationRuleFiringRepository firingRepository;
  private NotificationRuleEngine ruleEngine;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:notification-rules-handler-"
            + UUID.randomUUID().toString().replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));

    var repos = CrudRepositories.over(dataSource);
    var ruleRepository = new NotificationRuleRepository(repos.notificationRules(),
            repos.dslQueries());
    firingRepository = new NotificationRuleFiringRepository(
            repos.notificationRuleFirings(), repos.dslQueries());

    ruleService = new NotificationRuleService(ruleRepository, objectMapper,
            new NullProvider<>(() -> null));

    var executor = new ThreadPoolTaskExecutor();
    executor.initialize();
    ruleEngine = new NotificationRuleEngine(ruleRepository, firingRepository, ruleService,
            List.of(), executor);

    var handler = new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.emptyProvider(),
            AuditTestSupport.emptyMailSenderProvider());
    var router = new NotificationRouterConfiguration();

    var adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    var exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.notificationRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void createReturns201WithRedactedSecret() throws Exception {
    String body = objectMapper.writeValueAsString(sampleRule("loan-failures"));

    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andExpect(jsonPath("$.name").value("loan-failures"))
            .andExpect(jsonPath("$.enabled").value(true))
            .andExpect(jsonPath("$.eventFilter.eventType").value("RunFailed"))
            .andExpect(jsonPath("$.actions[0].sink").value("webhook"))
            .andExpect(jsonPath("$.actions[0].secret").isEmpty())
            .andExpect(jsonPath("$.priority").value(5))
            .andExpect(jsonPath("$.rateClass").value("default"));
  }

  @Test
  void listReturnsPagedEnvelopeWithCreatedRule() throws Exception {
    ruleService.create(sampleRule("rule-a"));
    ruleService.create(sampleRule("rule-b"));

    mockMvc.perform(get("/api/dsl/notifications/rules").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(1))
            .andExpect(jsonPath("$.items[0].name").value("rule-a"))
            .andExpect(jsonPath("$.items[0].actions[0].secret").isEmpty());
  }

  @Test
  void getByIdReturns200WhenFound() throws Exception {
    var created = ruleService.create(sampleRule("by-id"));

    mockMvc.perform(get("/api/dsl/notifications/rules/" + created.id()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id()))
            .andExpect(jsonPath("$.name").value("by-id"))
            .andExpect(jsonPath("$.actions[0].secret").isEmpty());
  }

  @Test
  void getByIdReturns404EnvelopeWhenMissing() throws Exception {
    mockMvc.perform(get("/api/dsl/notifications/rules/987654321"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Notification rule not found: 987654321"));
  }

  @Test
  void updateReturns200WithReplacedRule() throws Exception {
    var created = ruleService.create(sampleRule("old-name"));
    var updated = new NotificationRuleDto(
            created.id(), "new-name", false,
            new EventFilterDto("RunCompleted", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")),
            10, "urgent", null, null);

    mockMvc.perform(put("/api/dsl/notifications/rules/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id()))
            .andExpect(jsonPath("$.name").value("new-name"))
            .andExpect(jsonPath("$.enabled").value(false))
            .andExpect(jsonPath("$.eventFilter.eventType").value("RunCompleted"))
            .andExpect(jsonPath("$.priority").value(10))
            .andExpect(jsonPath("$.rateClass").value("urgent"));
  }

  @Test
  void updateUnknownIdReturns404Envelope() throws Exception {
    var body = objectMapper.writeValueAsString(sampleRule("any"));

    mockMvc.perform(put("/api/dsl/notifications/rules/987654321")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Notification rule not found: 987654321"));
  }

  @Test
  void deleteReturns204AndSubsequentGet404() throws Exception {
    var created = ruleService.create(sampleRule("to-delete"));

    mockMvc.perform(delete("/api/dsl/notifications/rules/" + created.id()))
            .andExpect(status().isNoContent());

    mockMvc.perform(get("/api/dsl/notifications/rules/" + created.id()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void deleteUnknownIdReturns404Envelope() throws Exception {
    mockMvc.perform(delete("/api/dsl/notifications/rules/987654321"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Notification rule not found: 987654321"));
  }

  @Test
  void setEnabledReturnsUpdatedRule() throws Exception {
    var created = ruleService.create(sampleRule("toggle"));
    String body = objectMapper.writeValueAsString(new ToggleEnabledRequest(false));

    mockMvc.perform(post("/api/dsl/notifications/rules/" + created.id() + "/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(created.id()))
            .andExpect(jsonPath("$.name").value("toggle"))
            .andExpect(jsonPath("$.enabled").value(false));
  }

  @Test
  void setEnabledUnknownIdReturns404Envelope() throws Exception {
    String body = objectMapper.writeValueAsString(new ToggleEnabledRequest(true));

    mockMvc.perform(post("/api/dsl/notifications/rules/987654321/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Notification rule not found: 987654321"));
  }

  @Test
  void createWithBlankNameReturns400Envelope() throws Exception {
    var dto = new NotificationRuleDto(null, "   ", true,
            new EventFilterDto("RunFailed", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")),
            0, "default", null, null);

    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("'name' is required and must not be blank"));
  }

  @Test
  void createWithMissingEventTypeReturns400Envelope() throws Exception {
    var dto = new NotificationRuleDto(null, "no-event", true,
            new EventFilterDto(null, null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")),
            0, "default", null, null);

    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").isString());
  }

  @Test
  void createWithMalformedJsonReturns400() throws Exception {
    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{not-json"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void channelsReturnsStaticCatalogue() throws Exception {
    mockMvc.perform(get("/api/dsl/notifications/channels"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(4))
            .andExpect(jsonPath("$.items[0].type").value("webhook"))
            .andExpect(jsonPath("$.items[0].enabled").value(false))
            .andExpect(jsonPath("$.items[1].type").value("email"));
  }

  @Test
  void fireLogReturnsEmptyEnvelope() throws Exception {
    mockMvc.perform(get("/api/dsl/notifications/fire-log"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void testEventReturnsEmptyResultWhenNoRulesMatch() throws Exception {
    String body = "{\"eventType\":\"RunFailed\",\"processName\":\"LoanOrigination\"}";

    mockMvc.perform(post("/api/dsl/notifications/test")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.matchedRuleIds").isArray())
            .andExpect(jsonPath("$.matchedRuleIds").isEmpty())
            .andExpect(jsonPath("$.fireResults").isArray())
            .andExpect(jsonPath("$.fireResults").isEmpty());
  }

  @Test
  void testEventWithBlankEventTypeReturns400Envelope() throws Exception {
    mockMvc.perform(post("/api/dsl/notifications/test")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"eventType\":\"   \"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(
                    jsonPath("$.message").value("'eventType' is required and must not be blank"));
  }

  // ----- audit (T776) -------------------------------------------------------

  @Test
  void createWritesAuditRowWithIdNameEnabledAndOmitsSinkSecret() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));

    String body = objectMapper.writeValueAsString(sampleRule("loan-failures"));

    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isCreated());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_CREATE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isNotEqualTo("-");
    assertThat(row.detailsJson())
            .doesNotContain("topsecret") // sample rule secret
            .doesNotContain("http://example.com/hook"); // sink URL
    var details = objectMapper.readValue(row.detailsJson(), java.util.Map.class);
    assertThat(details).containsKeys("id", "name", "enabled");
    assertThat(details.get("name")).isEqualTo("loan-failures");
  }

  @Test
  void createWithBlankNameWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));

    var dto = new NotificationRuleDto(null, "   ", true,
            new EventFilterDto("RunFailed", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")),
            0, "default", null, null);

    mockMvc.perform(post("/api/dsl/notifications/rules")
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isBadRequest());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_CREATE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void updateWritesAuditRowOnSuccessAndOmitsSinkSecret() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));
    var created = ruleService.create(sampleRule("old-name"));
    var updated = new NotificationRuleDto(
            created.id(), "new-name", false,
            new EventFilterDto("RunCompleted", null, null, null, null),
            List.of(new NotificationActionDto("email", null, null, "ops@example.com")),
            10, "urgent", null, null);

    mockMvc.perform(put("/api/dsl/notifications/rules/" + created.id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updated)))
            .andExpect(status().isOk());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_UPDATE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(String.valueOf(created.id()));
  }

  @Test
  void updateUnknownIdWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));
    var body = objectMapper.writeValueAsString(sampleRule("any"));

    mockMvc.perform(put("/api/dsl/notifications/rules/987654321")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isNotFound());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_UPDATE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void deleteWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));
    var created = ruleService.create(sampleRule("to-delete"));

    mockMvc.perform(delete("/api/dsl/notifications/rules/" + created.id()))
            .andExpect(status().isNoContent());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_DELETE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(String.valueOf(created.id()));
  }

  @Test
  void deleteUnknownIdWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));

    mockMvc.perform(delete("/api/dsl/notifications/rules/987654321"))
            .andExpect(status().isNotFound());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_DELETE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void toggleWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));
    var created = ruleService.create(sampleRule("toggle"));
    String body = objectMapper.writeValueAsString(new ToggleEnabledRequest(false));

    mockMvc.perform(post("/api/dsl/notifications/rules/" + created.id() + "/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_TOGGLE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo(String.valueOf(created.id()));
    var details = objectMapper.readValue(row.detailsJson(), java.util.Map.class);
    assertThat(details.get("enabled")).isEqualTo(false);
  }

  @Test
  void toggleUnknownIdWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new NotificationRuleHandler(ruleService, ruleEngine, firingRepository,
            Optional.empty(), AuditTestSupport.providerOf(audit.service()),
            AuditTestSupport.emptyMailSenderProvider()));
    String body = objectMapper.writeValueAsString(new ToggleEnabledRequest(true));

    mockMvc.perform(post("/api/dsl/notifications/rules/987654321/enabled")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isNotFound());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("NOTIFICATION_RULE_TOGGLE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  private static MockMvc buildMockMvc(NotificationRuleHandler handler) {
    NotificationRouterConfiguration router = new NotificationRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders.routerFunctions(router.notificationRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  private NotificationRuleDto sampleRule(String name) {
    return new NotificationRuleDto(
            null,
            name,
            true,
            new EventFilterDto("RunFailed", null, null, "Loan*", null),
            List.of(new NotificationActionDto("webhook", "http://example.com/hook", "topsecret",
                    null)),
            5,
            "default",
            null,
            null);
  }

  private record NullProvider<T>(Supplier<T> supplier) implements ObjectProvider<T> {

    @Override
    public T getIfAvailable() {
      return supplier.get();
    }

    @Override
    public T getIfUnique() {
      return supplier.get();
    }

    @Override
    public T getObject() {
      return supplier.get();
    }
  }
}
