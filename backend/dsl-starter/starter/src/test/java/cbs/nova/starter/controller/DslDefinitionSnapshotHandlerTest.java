package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.router.DslDefinitionSnapshotRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.persistence.CrudRepositories;
import cbs.nova.starter.service.DefinitionSnapshotService;
import java.util.List;
import java.util.Map;
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

/**
 * Read-side tests for {@code GET /api/dsl/definitions/{name}/versions{,/hash}} (T875): version list
 * ordering, snapshot source + unified diff payload, and the 404 envelope on an unknown hash.
 */
class DslDefinitionSnapshotHandlerTest {

  private MockMvc mockMvc;
  private DefinitionSnapshotService service;

  @BeforeEach
  void setUp() throws Exception {
    var dataSource = new JdbcDataSource();
    dataSource.setURL("jdbc:h2:mem:snapshot-handler-" + UUID.randomUUID().toString()
            .replace("-", "")
            + ";DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE"
            + ";CASE_INSENSITIVE_IDENTIFIERS=TRUE");
    dataSource.setUser("sa");
    var connection = dataSource.getConnection();
    ScriptUtils.executeSqlScript(connection,
            new ClassPathResource("db/migration/h2/V1__init.sql"));
    ScriptUtils.executeSqlScript(connection,
            new ClassPathResource("db/migration/h2/V3__dsl_definition_snapshots.sql"));
    connection.close();
    service = new DefinitionSnapshotService(
            CrudRepositories.over(dataSource).dslDefinitionSnapshots());
    GlobalManager.globalManager().resetForTests();

    var handler = new DslDefinitionSnapshotHandler(service);
    DslDefinitionSnapshotRouterConfiguration router = new DslDefinitionSnapshotRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslDefinitionSnapshotRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void versionsListsCapturedSnapshotsNewestFirst() throws Exception {
    capture("LoanDsl", "v1", "reload");
    capture("LoanDsl", "v2", "publish");

    mockMvc.perform(get("/api/dsl/definitions/LoanDsl/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].capturedBy").value("publish"))
            .andExpect(jsonPath("$[0].contentHash").value(matchesPattern("[0-9a-f]{64}")))
            .andExpect(jsonPath("$[0].version").isString())
            .andExpect(jsonPath("$[0].capturedAt").isString())
            .andExpect(jsonPath("$[1].capturedBy").value("reload"));
  }

  @Test
  void versionsOfUnknownDefinitionIsEmptyList() throws Exception {
    mockMvc.perform(get("/api/dsl/definitions/missing/versions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$", hasSize(0)));
  }

  @Test
  void versionReturnsSourceTextAndDiffVsPrevious() throws Exception {
    capture("LoanDsl", "before", "reload");
    capture("LoanDsl", "after", "publish");
    String newestHash = service.versions("LoanDsl").get(0).contentHash();

    mockMvc.perform(get("/api/dsl/definitions/LoanDsl/versions/{hash}", newestHash))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("LoanDsl"))
            .andExpect(jsonPath("$.contentHash").value(newestHash))
            .andExpect(jsonPath("$.capturedBy").value("publish"))
            .andExpect(
                    jsonPath("$.sourceText").value(org.hamcrest.Matchers.containsString("after")))
            .andExpect(jsonPath("$.diff").value(org.hamcrest.Matchers.containsString("+after")));
  }

  @Test
  void firstVersionHasNullDiff() throws Exception {
    capture("LoanDsl", "v1", "reload");
    String hash = service.versions("LoanDsl").get(0).contentHash();

    mockMvc.perform(get("/api/dsl/definitions/LoanDsl/versions/{hash}", hash))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sourceText").isString())
            .andExpect(jsonPath("$.diff").doesNotExist());
  }

  @Test
  void unknownHashReturns404NotFoundEnvelope() throws Exception {
    capture("LoanDsl", "v1", "reload");

    mockMvc.perform(get("/api/dsl/definitions/LoanDsl/versions/{hash}", "0".repeat(64)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private void capture(String name, String content, String capturedBy) {
    var gm = GlobalManager.globalManager();
    if (gm.describeProcess(name).isEmpty()) {
      gm.registerProcess(Dsl.process(name).execute(ctx -> Result.success("ok")).build());
    }
    service.capture(Map.of(name + ".java", content), gm, capturedBy);
  }
}
