package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.router.DslEventRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-side tests for {@code GET /api/dsl/events} (T411). Pins the T382 {@code PageResponse}
 * envelope, the four filter dimensions (type, aggregateType, aggregateId, correlationId, since),
 * pagination clamping, and the 400 envelope on a malformed {@code since} value.
 */
class DslEventHandlerTest {

  private MockMvc mockMvc;
  private DslEventRepository repository;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:events-handler-" + UUID.randomUUID().toString()
            .replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    ScriptUtils.executeSqlScript(dataSource.getConnection(),
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    var repos = CrudRepositories.over(dataSource);
    repository = new DslEventRepository(repos.dslEvents(), repos.dslQueries());

    DslEventHandler handler = new DslEventHandler(repository, new ObjectMapper());
    DslEventRouterConfiguration router = new DslEventRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslEventRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void emptyTableReturnsEmptyEnvelope() throws Exception {
    mockMvc.perform(get("/api/dsl/events"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listReturnsNewestFirstWithEnvelope() throws Exception {
    seed(5);

    mockMvc.perform(get("/api/dsl/events").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].aggregateId").value("run-4"))
            .andExpect(jsonPath("$.items[1].aggregateId").value("run-3"))
            .andExpect(jsonPath("$.items[0].eventType").value("RunStarted"))
            .andExpect(jsonPath("$.items[0].aggregateType").value("run"))
            .andExpect(jsonPath("$.items[0].schemaVersion").value(1))
            .andExpect(jsonPath("$.items[0].createdAt").exists())
            .andExpect(jsonPath("$.items[0].payload.runId").value("run-4"));
  }

  @Test
  void offsetSkipsRows() throws Exception {
    seed(5);

    mockMvc.perform(get("/api/dsl/events").param("limit", "2").param("offset", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(3))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].aggregateId").value("run-1"))
            .andExpect(jsonPath("$.items[1].aggregateId").value("run-0"));
  }

  @Test
  void typeFilterNarrowsResults() throws Exception {
    Instant now = Instant.now();
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-1",
            null, "{\"runId\":\"run-1\"}", 1, now.minus(1, ChronoUnit.MINUTES)));
    repository.insert(new DslEventEntity(null, "RunCompleted", "run", "run-1",
            "corr-1", "{\"status\":\"COMPLETED\"}", 1, now));
    repository.insert(new DslEventEntity(null, "DraftPublished", "definition", "def-A",
            null, "{\"definitionName\":\"def-A\"}", 1, now));

    mockMvc.perform(get("/api/dsl/events").param("type", "RunCompleted"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].eventType").value("RunCompleted"))
            .andExpect(jsonPath("$.items[0].aggregateId").value("run-1"))
            .andExpect(jsonPath("$.items[0].correlationId").value("corr-1"))
            .andExpect(jsonPath("$.items[0].payload.status").value("COMPLETED"));
  }

  @Test
  void aggregateTypeAndIdFiltersNarrowTogether() throws Exception {
    Instant now = Instant.now();
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-1",
            null, "{}", 1, now));
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-2",
            null, "{}", 1, now));
    repository.insert(new DslEventEntity(null, "DraftPublished", "definition", "def-A",
            null, "{}", 1, now));

    mockMvc.perform(get("/api/dsl/events")
            .param("aggregateType", "run")
            .param("aggregateId", "run-2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].aggregateId").value("run-2"));
  }

  @Test
  void correlationIdFilterNarrowsResults() throws Exception {
    Instant now = Instant.now();
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-1",
            "corr-1", "{}", 1, now));
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-2",
            null, "{}", 1, now));
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-3",
            "corr-3", "{}", 1, now));

    mockMvc.perform(get("/api/dsl/events").param("correlationId", "corr-1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].aggregateId").value("run-1"));
  }

  @Test
  void sinceFilterIncludesRowsAtAndAfterInstant() throws Exception {
    Instant t0 = Instant.now();
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "old",
            null, "{}", 1, t0.minus(60, ChronoUnit.SECONDS)));
    repository.insert(new DslEventEntity(null, "RunStarted", "run", "new",
            null, "{}", 1, t0.plus(60, ChronoUnit.SECONDS)));

    mockMvc.perform(get("/api/dsl/events").param("since", t0.toString()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].aggregateId").value("new"));
  }

  @Test
  void limitIsClampedToMax() throws Exception {
    seed(3);

    mockMvc.perform(get("/api/dsl/events").param("limit", "99999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(500))
            .andExpect(jsonPath("$.items", hasSize(3)));
  }

  @Test
  void nonNumericLimitReturns400() throws Exception {
    mockMvc.perform(get("/api/dsl/events").param("limit", "abc"))
            .andExpect(status().isBadRequest());
  }

  @Test
  void malformedSinceReturns400() throws Exception {
    mockMvc.perform(get("/api/dsl/events").param("since", "not-an-instant"))
            .andExpect(status().isBadRequest());
  }

  private void seed(int count) {
    Instant now = Instant.now();
    for (int i = 0; i < count; i++) {
      repository.insert(new DslEventEntity(null, "RunStarted", "run", "run-" + i,
              null, "{\"runId\":\"run-" + i + "\"}", 1,
              now.minus(count - i, ChronoUnit.MINUTES)));
    }
  }
}
