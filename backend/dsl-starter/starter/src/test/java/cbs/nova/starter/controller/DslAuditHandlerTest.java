package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.router.DslAuditRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

/**
 * Read-side tests for {@code GET /api/dsl/audit}: T382 {@code PageResponse} envelope, pagination
 * clamping, and the {@code action} filter.
 */
class DslAuditHandlerTest {

  private AuditTestSupport.Harness audit;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    audit = AuditTestSupport.h2();
    DslAuditHandler handler = new DslAuditHandler(audit.service());
    DslAuditRouterConfiguration router = new DslAuditRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslAuditRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void emptyTableReturnsEmptyEnvelope() throws Exception {
    mockMvc.perform(get("/api/dsl/audit"))
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

    mockMvc.perform(get("/api/dsl/audit").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].target").value("target-4"))
            .andExpect(jsonPath("$.items[1].target").value("target-3"))
            .andExpect(jsonPath("$.items[0].action").value("DRAFT_WRITE"))
            .andExpect(jsonPath("$.items[0].outcome").value("SUCCESS"))
            .andExpect(jsonPath("$.items[0].actor").value("operator-1"))
            .andExpect(jsonPath("$.items[0].correlationId").doesNotExist())
            .andExpect(jsonPath("$.items[0].occurredAt").exists());
  }

  @Test
  void offsetSkipsRows() throws Exception {
    seed(5);

    mockMvc.perform(get("/api/dsl/audit").param("limit", "2").param("offset", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(3))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].target").value("target-1"))
            .andExpect(jsonPath("$.items[1].target").value("target-0"));
  }

  @Test
  void actionFilterNarrowsResults() throws Exception {
    Instant now = Instant.now();
    audit.append(new DslAudit(null, now.minus(1, ChronoUnit.MINUTES),
            "operator-1", "DRAFT_WRITE", "target-a", null, "SUCCESS", null));
    audit.append(new DslAudit(null, now, "operator-1",
            "DEFINITION_PUBLISH", "target-b", "corr-1", "FAILURE", "{\"x\":1}"));

    mockMvc.perform(get("/api/dsl/audit").param("action", "DEFINITION_PUBLISH"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].target").value("target-b"))
            .andExpect(jsonPath("$.items[0].correlationId").value("corr-1"))
            .andExpect(jsonPath("$.items[0].detailsJson").value("{\"x\":1}"));
  }

  @Test
  void limitIsClampedToMax() throws Exception {
    seed(3);

    mockMvc.perform(get("/api/dsl/audit").param("limit", "99999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(500))
            .andExpect(jsonPath("$.items", hasSize(3)));
  }

  @Test
  void nonNumericLimitReturns400() throws Exception {
    mockMvc.perform(get("/api/dsl/audit").param("limit", "abc"))
            .andExpect(status().isBadRequest());
  }

  private void seed(int count) {
    Instant now = Instant.now();
    for (int i = 0; i < count; i++) {
      audit.append(new DslAudit(null,
              now.minus(count - i, ChronoUnit.MINUTES), "operator-1", "DRAFT_WRITE",
              "target-" + i, null, "SUCCESS", null));
    }
  }
}
