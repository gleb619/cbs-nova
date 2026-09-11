package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.PreviewReport;
import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.router.DslDefinitionTestRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestCaseResult;
import cbs.nova.starter.model.DefinitionTestCaseStatus;
import cbs.nova.starter.model.DefinitionTestRunReport;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.service.DslDefinitionTestService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.json.JsonMapper;

/**
 * Handler-level tests for the definition test-case surface (T409). Pins the on-the-wire JSON shape
 * of {@code GET}, {@code PUT}, and {@code POST .../run} plus the 404 envelope on unknown
 * definitions, and asserts the {@code TESTS_RUN} audit hook fires when an audit service is
 * available.
 */
class DslDefinitionTestHandlerTest {

  private DslDefinitionTestService service;
  private AuditTestSupport.Harness audit;
  private MockMvc mockMvc;

  private final JsonMapper jackson3 = JsonMapper.builder().build();

  @BeforeEach
  void setUp() {
    service = mock(DslDefinitionTestService.class);
    audit = AuditTestSupport.h2();
    DslDefinitionTestHandler handler = new DslDefinitionTestHandler(service,
            AuditTestSupport.providerOf(audit.service()));
    DslDefinitionTestRouterConfiguration router = new DslDefinitionTestRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslDefinitionTestRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void listReturnsEmptyArrayWhenNoCasesStored() throws Exception {
    when(service.list("LoanDisbursement")).thenReturn(List.of());

    mockMvc.perform(get("/api/dsl/definitions/LoanDisbursement/tests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").isEmpty());
  }

  @Test
  void listReturnsCasesWithCaseNameInputExpectedOutput() throws Exception {
    when(service.list("LoanDisbursement")).thenReturn(List.of(
            new DefinitionTestCase("happy", request(Map.of("x", 1)), report(Map.of("y", 2))),
            new DefinitionTestCase("edge", request(Map.of("x", 3)), report(Map.of("y", 4)))));

    mockMvc.perform(get("/api/dsl/definitions/LoanDisbursement/tests"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].caseName").value("happy"))
            .andExpect(jsonPath("$[0].input.body.x").value(1))
            .andExpect(jsonPath("$[0].expectedOutput.output.y").value(2))
            .andExpect(jsonPath("$[1].caseName").value("edge"));
  }

  @Test
  void listOnUnknownDefinitionReturns404ErrorEnvelope() throws Exception {
    when(service.list("ghost")).thenThrow(new DefinitionNotFoundException("ghost"));

    mockMvc.perform(get("/api/dsl/definitions/ghost/tests"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No published definition: ghost"))
            .andExpect(jsonPath("$.entityName").value("ghost"));
  }

  @Test
  void putReplacesWholeCaseSetAndReturnsStoredSet() throws Exception {
    String body = """
            [{
              "caseName": "happy",
              "input": { "body": { "x": 1 }, "metadata": {} },
              "expectedOutput": {
                "name": "LoanDisbursement",
                "mode": "PREVIEW",
                "success": true,
                "output": { "y": 2 },
                "executionTrace": [],
                "externalCalls": [],
                "callCounts": {},
                "dryRunLogs": [],
                "errors": null,
                "metrics": null,
                "astTree": null
              }
            }]""";
    when(service.replaceAll(anyString(), any())).thenReturn(List.of(
            new DefinitionTestCase("happy", request(Map.of("x", 1)), report(Map.of("y", 2)))));

    mockMvc.perform(put("/api/dsl/definitions/LoanDisbursement/tests")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].caseName").value("happy"));
  }

  @Test
  void runReturnsReportShapeWithCasesAndSummary() throws Exception {
    DefinitionTestRunReport report = new DefinitionTestRunReport(2, 1, 1, 0, List.of(
            new DefinitionTestCaseResult("pass", DefinitionTestCaseStatus.PASS,
                    report(Map.of("y", 2)), report(Map.of("y", 2)), 5L, null),
            new DefinitionTestCaseResult("fail", DefinitionTestCaseStatus.FAIL,
                    report(Map.of("y", 9)), report(Map.of("y", 2)), 7L, null)));
    when(service.run(anyString(), any())).thenReturn(report);

    mockMvc.perform(post("/api/dsl/definitions/LoanDisbursement/tests/run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.passed").value(1))
            .andExpect(jsonPath("$.failed").value(1))
            .andExpect(jsonPath("$.errored").value(0))
            .andExpect(jsonPath("$.cases.length()").value(2))
            .andExpect(jsonPath("$.cases[0].name").value("pass"))
            .andExpect(jsonPath("$.cases[0].status").value("PASS"))
            .andExpect(jsonPath("$.cases[0].durationMs").value(5))
            .andExpect(jsonPath("$.cases[1].name").value("fail"))
            .andExpect(jsonPath("$.cases[1].status").value("FAIL"));
  }

  @Test
  void runForwardsCaseSubsetQueryParam() throws Exception {
    when(service.run(anyString(), any())).thenReturn(new DefinitionTestRunReport(0, 0, 0, 0,
            List.of()));

    mockMvc.perform(post("/api/dsl/definitions/LoanDisbursement/tests/run")
            .param("case", "happy")
            .param("case", "edge"))
            .andExpect(status().isOk());

    verify(service).run(eq("LoanDisbursement"),
            argThat(subset -> subset != null && subset.size() == 2 && subset.contains("happy")
                    && subset.contains("edge")));
  }

  @Test
  void runWritesTestsRunAuditEntry() throws Exception {
    DefinitionTestRunReport report = new DefinitionTestRunReport(3, 2, 1, 0, List.of());
    when(service.run(anyString(), any())).thenReturn(report);

    mockMvc.perform(post("/api/dsl/definitions/LoanDisbursement/tests/run"))
            .andExpect(status().isOk());

    var entries = audit.repository().search("TESTS_RUN", 0, 10);
    assertThat(entries.total()).isEqualTo(1);
    assertThat(entries.items().get(0).target()).isEqualTo("LoanDisbursement");
    assertThat(entries.items().get(0).outcome()).isEqualTo("SUCCESS");
  }

  @Test
  void runRecordsFailureOutcomeWhenAnyCaseErrored() throws Exception {
    DefinitionTestRunReport report = new DefinitionTestRunReport(3, 1, 1, 1, List.of());
    when(service.run(anyString(), any())).thenReturn(report);

    mockMvc.perform(post("/api/dsl/definitions/LoanDisbursement/tests/run"))
            .andExpect(status().isOk());

    var entries = audit.repository().search("TESTS_RUN", 0, 10);
    assertThat(entries.items().get(0).outcome()).isEqualTo("FAILURE");
  }

  @Test
  void runOnUnknownDefinitionReturns404AndDoesNotAudit() throws Exception {
    doAnswer(inv -> {
      throw new DefinitionNotFoundException("ghost");
    }).when(service).run(anyString(), any());

    mockMvc.perform(post("/api/dsl/definitions/ghost/tests/run"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

    assertThat(audit.repository().search("TESTS_RUN", 0, 10).total()).isEqualTo(0);
  }

  private DslRequest request(Object body) {
    return new DslRequest(body, Map.of());
  }

  private PreviewReport report(Object output) {
    return new PreviewReport("LoanDisbursement", ExecutionMode.PREVIEW, true, output,
            List.of(), List.of(), Map.of(), null, List.of(), null, null);
  }
}
