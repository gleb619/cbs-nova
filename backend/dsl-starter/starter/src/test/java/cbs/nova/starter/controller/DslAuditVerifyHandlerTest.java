package cbs.nova.starter.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.AuditTestSupport.Harness;
import cbs.nova.starter.config.router.DslAuditRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.DslAudit;
import cbs.nova.starter.security.JsonRedactor;
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
 * T880 — handler-level tests for {@code GET /api/dsl/audit/verify}. Wires the audit router with a
 * real {@link cbs.nova.starter.persistence.DslAuditStore} backed by an in-memory H2 instance via
 * {@link AuditTestSupport}. The router-level @RouterOperation contract is exercised in
 * {@link cbs.nova.starter.config.router.RouterOperationConsistencyTest}.
 */
class DslAuditVerifyHandlerTest {

  private Harness audit;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    audit = AuditTestSupport.h2();
    DslAuditHandler handler = new DslAuditHandler(audit.service(), audit.store(),
            JsonRedactor.disabled());
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
  void verifyOnEmptyTableReturnsOk() throws Exception {
    mockMvc.perform(get("/api/dsl/audit/verify"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.checked").value(0))
            .andExpect(jsonPath("$.firstDivergenceId").doesNotExist())
            .andExpect(jsonPath("$.firstDivergenceAt").doesNotExist())
            .andExpect(jsonPath("$.reason").doesNotExist());
  }

  @Test
  void verifyOnCleanChainReturnsOk() throws Exception {
    Instant now = Instant.now();
    audit.append(new DslAudit(null, now, "op-1", "DEFINITION_PUBLISH", "LoanFlow",
            null, "SUCCESS", "{\"n\":1}", null, null));
    audit.append(new DslAudit(null, now.plus(1, ChronoUnit.MINUTES), "op-2",
            "DRAFT_WRITE", "LoanFlow", null, "SUCCESS", "{\"n\":2}", null, null));

    mockMvc.perform(get("/api/dsl/audit/verify"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ok").value(true))
            .andExpect(jsonPath("$.checked").value(2))
            .andExpect(jsonPath("$.firstDivergenceId").doesNotExist())
            .andExpect(jsonPath("$.firstDivergenceAt").doesNotExist())
            .andExpect(jsonPath("$.reason").doesNotExist());
  }
}
