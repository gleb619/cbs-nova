package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.number.IsCloseTo.closeTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.dsl.repository.InMemoryTransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.starter.config.DslExecutionsRouterConfiguration;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.persistence.DslRunStats;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import cbs.nova.starter.service.DslRunCancellationService;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

class DslExecutionsResourceTest {

  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private final TransactionExecutionRepository transactionExecutionRepository = new InMemoryTransactionExecutionRepository();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final WorkflowClient workflowClient = mock(WorkflowClient.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DslRunCancellationService cancellationService = new DslRunCancellationService(workflowClient,
            repository);
    DslExecutionsHandler handler = new DslExecutionsHandler(repository, objectMapper,
            cancellationService, null, transactionExecutionRepository);
    DslExecutionsRouterConfiguration router = new DslExecutionsRouterConfiguration();
    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslExecutionsRouter(handler))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void emptyRepositoryReturnsEmptyItemsAndZeroTotal() throws Exception {
    mockMvc.perform(get("/api/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items.length()").value(0))
            .andExpect(jsonPath("$.total").value(0));
  }

  @Test
  void returnsAllRunsWithoutFilters() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "FAILED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:02Z", "PREVIEW"));

    mockMvc.perform(get("/api/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[?(@.id=='run-1')]").exists())
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").exists());
  }

  @Test
  void filtersByProcessName() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "CreditScoring", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("processName", "LoanDisbursement"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-1"))
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").doesNotExist());
  }

  @Test
  void filtersByRawBackendStatusName() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "STALE", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("status", "STALE"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-2"))
            .andExpect(jsonPath("$.items[0].status").value("Stale"))
            .andExpect(jsonPath("$.items[?(@.id=='run-1')]").doesNotExist());
  }

  @Test
  void statusFilterIsCaseInsensitiveMatchingDisplayCasing() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "STALE", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("status", "Completed"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-1"))
            .andExpect(jsonPath("$.items[0].status").value("Completed"))
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").doesNotExist());
  }

  @Test
  void modeFilterMatchesStoredRunModeCaseInsensitively() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "PREVIEW"));

    mockMvc.perform(get("/api/executions").param("mode", "RUN"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-1"))
            .andExpect(jsonPath("$.items[0].mode").value("RUN"))
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").doesNotExist());
  }

  @Test
  void modeFilterDefaultsNullExecutionModeToRun() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", null));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "PREVIEW"));

    mockMvc.perform(get("/api/executions").param("mode", "run"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-1"))
            .andExpect(jsonPath("$.items[0].mode").value("RUN"))
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").doesNotExist());
  }

  @Test
  void modeFilterExcludesNonMatchingRuns() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "PREVIEW"));

    mockMvc.perform(get("/api/executions").param("mode", "PREVIEW"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-2"))
            .andExpect(jsonPath("$.items[0].mode").value("PREVIEW"))
            .andExpect(jsonPath("$.items[?(@.id=='run-1')]").doesNotExist());
  }

  @Test
  void combinesProcessNameAndStatusFilters() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "FAILED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));
    repository.save(run("run-3", "CreditScoring", "FAILED", "2026-08-13T10:02:00Z",
            "2026-08-13T10:02:05Z", "RUN"));

    mockMvc.perform(get("/api/executions")
            .param("processName", "LoanDisbursement")
            .param("status", "FAILED"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-2"));
  }

  @Test
  void filtersByCorrelationId() throws Exception {
    repository.save(runWithCorrelationId("run-1", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", "corr-abc"));
    repository.save(runWithCorrelationId("run-2", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:01:00Z", "2026-08-13T10:01:05Z", "RUN", "corr-xyz"));
    repository.save(run("run-3", "LoanDisbursement", "COMPLETED", "2026-08-13T10:02:00Z",
            "2026-08-13T10:02:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("correlationId", "corr-abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-1"))
            .andExpect(jsonPath("$.items[0].correlationId").value("corr-abc"))
            .andExpect(jsonPath("$.items[?(@.id=='run-2')]").doesNotExist())
            .andExpect(jsonPath("$.items[?(@.id=='run-3')]").doesNotExist());
  }

  @Test
  void detailIncludesCorrelationIdWhenPresent() throws Exception {
    repository.save(runWithCorrelationId("run-corr", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", "corr-present"));

    mockMvc.perform(get("/api/executions/run-corr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correlationId").value("corr-present"));
  }

  @Test
  void detailOmitsCorrelationIdWhenAbsent() throws Exception {
    repository.save(run("run-no-corr", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/run-no-corr"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.correlationId").doesNotExist());
  }

  @Test
  void limitCapsItemsButNotTotal() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void offsetOnlySkipsRowsBeforeApplyingLimit() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));
    repository.save(run("run-3", "LoanDisbursement", "COMPLETED", "2026-08-13T10:02:00Z",
            "2026-08-13T10:02:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("offset", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void offsetAndLimitCombineToReturnWindow() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));
    repository.save(run("run-3", "LoanDisbursement", "COMPLETED", "2026-08-13T10:02:00Z",
            "2026-08-13T10:02:05Z", "RUN"));

    mockMvc.perform(get("/api/executions")
            .param("offset", "1")
            .param("limit", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.items.length()").value(1));
  }

  @Test
  void offsetBeyondTotalReturnsEmptyItemsWithCorrectTotal() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("offset", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void negativeOffsetIsClampedToZero() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("offset", "-5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void getByKnownIdReturnsMappedExecutionDto() throws Exception {
    repository.save(run("run-abc", "LoanDisbursement", "STALE", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", null));

    mockMvc.perform(get("/api/executions/run-abc"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("run-abc"))
            .andExpect(jsonPath("$.entity").value("LoanDisbursement"))
            .andExpect(jsonPath("$.entityType").value("Process"))
            .andExpect(jsonPath("$.mode").value("RUN"))
            .andExpect(jsonPath("$.status").value("Stale"))
            .andExpect(jsonPath("$.startedAt").value("2026-08-13T10:00:00Z"))
            .andExpect(jsonPath("$.completedAt").value("2026-08-13T10:00:05Z"))
            .andExpect(jsonPath("$.duration").value(5000));
  }

  @Test
  void getByKnownIdUppercasesModeAndOmitsOptionalFieldsWhenAbsent() throws Exception {
    repository.save(run("run-lower", "LoanDisbursement", "RUNNING", "2026-08-13T10:00:00Z",
            null, "preview"));

    mockMvc.perform(get("/api/executions/run-lower"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mode").value("PREVIEW"))
            .andExpect(jsonPath("$.status").value("Running"))
            .andExpect(jsonPath("$.completedAt").doesNotExist())
            .andExpect(jsonPath("$.duration").doesNotExist())
            .andExpect(jsonPath("$.retries").doesNotExist())
            .andExpect(jsonPath("$.triggeredBy").doesNotExist())
            .andExpect(jsonPath("$.correlationId").doesNotExist())
            .andExpect(jsonPath("$.workflowId").doesNotExist());
  }

  @Test
  void getByUnknownIdReturns404WithErrorResponse() throws Exception {
    mockMvc.perform(get("/api/executions/missing"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Execution run not found: missing"))
            .andExpect(jsonPath("$.runId").value("missing"));
  }

  @Test
  void getByKnownIdParsesValidJsonInputOutputAndMapsError() throws Exception {
    repository.save(run("run-json", "LoanDisbursement", "FAILED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN",
            "{\"amount\":100,\"currency\":\"USD\"}",
            "{\"approved\":false}",
            "insufficient funds"));

    mockMvc.perform(get("/api/executions/run-json"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.input.amount").value(100))
            .andExpect(jsonPath("$.input.currency").value("USD"))
            .andExpect(jsonPath("$.output.approved").value(false))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].message").value("insufficient funds"))
            .andExpect(jsonPath("$.errors[0].code").doesNotExist())
            .andExpect(jsonPath("$.errors[0].stackTrace").doesNotExist());
  }

  @Test
  void getByKnownIdFallsBackToRawStringForMalformedInputOutputJson() throws Exception {
    repository.save(run("run-raw", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN",
            "not json {{{",
            "plain-text-output",
            null));

    mockMvc.perform(get("/api/executions/run-raw"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.input").value("not json {{{"))
            .andExpect(jsonPath("$.output").value("plain-text-output"))
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()").value(0));
  }

  @Test
  void getByKnownIdOmitsInputOutputWhenAbsentAndReturnsEmptyErrorsWhenNoError() throws Exception {
    repository.save(run("run-empty", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/run-empty"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.input").doesNotExist())
            .andExpect(jsonPath("$.output").doesNotExist())
            .andExpect(jsonPath("$.errors").isArray())
            .andExpect(jsonPath("$.errors.length()").value(0));
  }

  // -------------------------------------------------------------------------
  // T296 — trace surfaced in the detail response.
  // -------------------------------------------------------------------------

  @Test
  void detailMapsTraceStepsFromContextJsonInOrder() throws Exception {
    String contextJson = objectMapper.writeValueAsString(Map.of("trace", List.of(
            "called helper: lookup",
            "executed transaction: apply",
            "called transaction: settle",
            "compensation log: rolled back settle",
            "process finished")));
    repository.save(runWithContext("run-trace", "LoanDisbursement", "FAILED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", contextJson));

    mockMvc.perform(get("/api/executions/run-trace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace.length()").value(5))
            .andExpect(jsonPath("$.trace[0].id").value("0"))
            .andExpect(jsonPath("$.trace[0].stepType").value("Helper"))
            .andExpect(jsonPath("$.trace[0].name").value("lookup"))
            .andExpect(jsonPath("$.trace[0].isCompensation").value(false))
            .andExpect(jsonPath("$.trace[1].stepType").value("Transaction"))
            .andExpect(jsonPath("$.trace[1].name").value("apply"))
            .andExpect(jsonPath("$.trace[2].stepType").value("Transaction"))
            .andExpect(jsonPath("$.trace[2].name").value("settle"))
            .andExpect(jsonPath("$.trace[3].stepType").value("Process"))
            .andExpect(jsonPath("$.trace[3].name").value("rolled back settle"))
            .andExpect(jsonPath("$.trace[3].isCompensation").value(true))
            .andExpect(jsonPath("$.trace[4].isCompensation").value(true));
  }

  @Test
  void detailOmitsTraceFieldWhenContextJsonIsAbsent() throws Exception {
    repository.save(run("run-no-context", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/run-no-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void detailOmitsTraceFieldWhenContextJsonIsMalformed() throws Exception {
    repository.save(runWithContext("run-bad-context", "LoanDisbursement", "FAILED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN",
            "this is not json at all"));

    mockMvc.perform(get("/api/executions/run-bad-context"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void detailOmitsTraceFieldWhenTraceArrayIsEmpty() throws Exception {
    String contextJson = objectMapper.writeValueAsString(Map.of("trace", List.of()));
    repository.save(runWithContext("run-empty-trace", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", contextJson));

    mockMvc.perform(get("/api/executions/run-empty-trace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace").doesNotExist());
  }

  @Test
  void detailCapsTraceAtFiveHundredAndAppendsTruncationMarker() throws Exception {
    List<String> entries = new java.util.ArrayList<>(501);
    for (int i = 0; i < 501; i++) {
      entries.add("called helper: h" + i);
    }
    String contextJson = objectMapper.writeValueAsString(Map.of("trace", entries));
    repository.save(runWithContext("run-big-trace", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", contextJson));

    mockMvc.perform(get("/api/executions/run-big-trace"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.trace.length()").value(501))
            .andExpect(jsonPath("$.trace[0].id").value("0"))
            .andExpect(jsonPath("$.trace[499].id").value("499"))
            .andExpect(jsonPath("$.trace[500].id").value("500"))
            .andExpect(jsonPath("$.trace[500].stepType").value("Process"))
            .andExpect(jsonPath("$.trace[500].name").value("… trace truncated (501 entries)"))
            .andExpect(jsonPath("$.trace[500].isCompensation").value(false));
  }

  @Test
  void listEndpointDoesNotIncludeTraceField() throws Exception {
    String contextJson = objectMapper.writeValueAsString(Map.of("trace",
            List.of("called helper: h0")));
    repository.save(runWithContext("run-list-trace", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN", contextJson));

    mockMvc.perform(get("/api/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].trace").doesNotExist());
  }

  @Test
  void listEndpointOmitsInputOutputAndErrorsFields() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "FAILED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN",
            "{\"amount\":1}", "{\"approved\":true}", "boom"));

    mockMvc.perform(get("/api/executions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].input").doesNotExist())
            .andExpect(jsonPath("$.items[0].output").doesNotExist())
            .andExpect(jsonPath("$.items[0].errors").doesNotExist());
  }

  @Test
  void cancelUnknownRunReturns404WithErrorResponse() throws Exception {
    mockMvc.perform(post("/api/executions/missing/cancel"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Execution run not found: missing"))
            .andExpect(jsonPath("$.runId").value("missing"));

    verifyNoInteractions(workflowClient);
  }

  @Test
  void cancelNonRunningRunReturns409AndLeavesStatusUntouched() throws Exception {
    repository.save(run("run-done", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(post("/api/executions/run-done/cancel"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message")
                    .value("Execution run is not cancellable: run-done (status COMPLETED)"))
            .andExpect(jsonPath("$.runId").value("run-done"));

    verifyNoInteractions(workflowClient);
    assertThat(repository.findByRunId("run-done").orElseThrow().status()).isEqualTo("COMPLETED");
  }

  @Test
  void cancelStaleRunReturns409BecauseSweepAlreadyEndedIt() throws Exception {
    repository.save(run("run-stale", "LoanDisbursement", "STALE", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(post("/api/executions/run-stale/cancel"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                    .value("Execution run is not cancellable: run-stale (status STALE)"));

    verifyNoInteractions(workflowClient);
  }

  @Test
  void cancelRunningRunCancelsWorkflowAndRecordsCancelledStatus() throws Exception {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-live")).thenReturn(stub);
    repository.save(run("run-live", "LoanDisbursement", "RUNNING", "2026-08-13T10:00:00Z",
            null, "RUN"));

    mockMvc.perform(post("/api/executions/run-live/cancel"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("run-live"))
            .andExpect(jsonPath("$.status").value("Cancelled"))
            .andExpect(jsonPath("$.errors.length()").value(1))
            .andExpect(jsonPath("$.errors[0].message").value("Cancelled by user"));

    verify(stub).cancel();
    assertThat(repository.findByRunId("run-live").orElseThrow().status()).isEqualTo("CANCELLED");
  }

  @Test
  void cancelledRunIsVisibleInListAndDetailWithNewStatus() throws Exception {
    WorkflowStub stub = mock(WorkflowStub.class);
    when(workflowClient.newUntypedWorkflowStub("run-live")).thenReturn(stub);
    repository.save(run("run-live", "LoanDisbursement", "RUNNING", "2026-08-13T10:00:00Z",
            null, "RUN"));

    mockMvc.perform(post("/api/executions/run-live/cancel")).andExpect(status().isOk());

    mockMvc.perform(get("/api/executions").param("status", "Cancelled"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items[0].id").value("run-live"))
            .andExpect(jsonPath("$.items[0].status").value("Cancelled"));

    mockMvc.perform(get("/api/executions/run-live"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("Cancelled"))
            .andExpect(jsonPath("$.completedAt").exists());
  }

  @Test
  void statsOnEmptyRepositoryReturnsZeroedShape() throws Exception {
    mockMvc.perform(get("/api/executions/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRuns").value(0))
            .andExpect(jsonPath("$.statusCounts").isEmpty())
            .andExpect(jsonPath("$.windowRuns").value(0))
            .andExpect(jsonPath("$.windowFailedRuns").value(0))
            .andExpect(jsonPath("$.windowFailureRate").value(0.0))
            .andExpect(jsonPath("$.windowHours").value(24))
            .andExpect(jsonPath("$.topProcesses").isArray())
            .andExpect(jsonPath("$.topProcesses.length()").value(0));
  }

  @Test
  void statsAggregatesAllRunsWhenRepositoryCannotAggregateServerSide() throws Exception {
    Instant now = Instant.now();
    repository.save(run("run-now-1", "LoanDisbursement", "COMPLETED",
            now.minus(Duration.ofHours(1)).toString(), now.toString(), "RUN"));
    repository.save(run("run-now-2", "LoanDisbursement", "FAILED",
            now.minus(Duration.ofHours(2)).toString(),
            now.minus(Duration.ofHours(2)).plusSeconds(5).toString(), "RUN"));
    repository.save(run("run-now-3", "CreditScoring", "RUNNING", now.toString(), null, "RUN"));
    // Started 30h ago: counted in totals but outside the trailing 24h window.
    repository.save(run("run-old-1", "CreditScoring", "COMPLETED",
            now.minus(Duration.ofHours(30)).toString(),
            now.minus(Duration.ofHours(29)).toString(), "RUN"));

    mockMvc.perform(get("/api/executions/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRuns").value(4))
            .andExpect(jsonPath("$.statusCounts.Completed").value(2))
            .andExpect(jsonPath("$.statusCounts.Failed").value(1))
            .andExpect(jsonPath("$.statusCounts.Running").value(1))
            .andExpect(jsonPath("$.windowRuns").value(3))
            .andExpect(jsonPath("$.windowFailedRuns").value(1))
            .andExpect(jsonPath("$.windowFailureRate").value(closeTo(1.0 / 3.0, 1e-9)))
            .andExpect(jsonPath("$.topProcesses.length()").value(2))
            .andExpect(jsonPath("$.topProcesses[0].processName").value("CreditScoring"))
            .andExpect(jsonPath("$.topProcesses[0].runCount").value(2))
            .andExpect(jsonPath("$.topProcesses[1].processName").value("LoanDisbursement"))
            .andExpect(jsonPath("$.topProcesses[1].runCount").value(2));
  }

  @Test
  void statsPrefersServerSideAggregatesWhenRepositorySupportsThem() throws Exception {
    DslRunStatsRepository statsRepository = mock(DslRunStatsRepository.class);
    when(statsRepository.stats(any(Instant.class), eq(5))).thenReturn(new DslRunStats(
            42,
            Map.of("RUNNING", 7L, "FAILED", 3L),
            10,
            2,
            0.2,
            List.of(new DslRunStats.ProcessRunCount("LoanDisbursement", 20))));
    DslExecutionsHandler handler = new DslExecutionsHandler(repository, objectMapper,
            new DslRunCancellationService(workflowClient, repository), statsRepository,
            transactionExecutionRepository);
    mockMvc = MockMvcBuilders
            .routerFunctions(new DslExecutionsRouterConfiguration().dslExecutionsRouter(handler))
            .build();

    mockMvc.perform(get("/api/executions/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRuns").value(42))
            .andExpect(jsonPath("$.statusCounts.Running").value(7))
            .andExpect(jsonPath("$.statusCounts.Failed").value(3))
            .andExpect(jsonPath("$.windowRuns").value(10))
            .andExpect(jsonPath("$.windowFailedRuns").value(2))
            .andExpect(jsonPath("$.windowFailureRate").value(0.2))
            .andExpect(jsonPath("$.topProcesses[0].processName").value("LoanDisbursement"))
            .andExpect(jsonPath("$.topProcesses[0].runCount").value(20));

    verify(statsRepository).stats(any(Instant.class), eq(5));
  }

  @Test
  void statsClampsTopProcessesParameter() throws Exception {
    DslRunStatsRepository statsRepository = mock(DslRunStatsRepository.class);
    when(statsRepository.stats(any(Instant.class), anyInt())).thenReturn(new DslRunStats(
            0, Map.of(), 0, 0, 0.0, List.of()));
    DslExecutionsHandler handler = new DslExecutionsHandler(repository, objectMapper,
            new DslRunCancellationService(workflowClient, repository), statsRepository,
            transactionExecutionRepository);
    mockMvc = MockMvcBuilders
            .routerFunctions(new DslExecutionsRouterConfiguration().dslExecutionsRouter(handler))
            .build();

    mockMvc.perform(get("/api/executions/stats").param("topProcesses", "99"))
            .andExpect(status().isOk());
    verify(statsRepository).stats(any(Instant.class), eq(20));

    mockMvc.perform(get("/api/executions/stats").param("topProcesses", "0"))
            .andExpect(status().isOk());
    verify(statsRepository).stats(any(Instant.class), eq(1));
  }

  @Test
  void statsLiteralRouteIsNotCapturedAsDetailId() throws Exception {
    repository.save(run("stats", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/stats"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRuns").exists())
            .andExpect(jsonPath("$.id").doesNotExist());
  }

  // -------------------------------------------------------------------------
  // T312 — transaction executions surfaced for a run.
  // -------------------------------------------------------------------------

  @Test
  void transactionsForUnknownRunReturns404WithErrorResponse() throws Exception {
    mockMvc.perform(get("/api/executions/missing/transactions"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Execution run not found: missing"))
            .andExpect(jsonPath("$.runId").value("missing"));
  }

  @Test
  void transactionsForKnownRunWithNoRowsReturnsEmptyArray() throws Exception {
    repository.save(run("run-empty-tx", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/run-empty-tx/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void transactionsForKnownRunReturnsRowsNewestFirstWithFields() throws Exception {
    repository.save(run("run-tx", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN"));
    transactionExecutionRepository.save(tx("run-tx", "first", Map.of("amount", 100),
            "2026-08-13T10:00:01Z"));
    transactionExecutionRepository.save(tx("run-tx", "second", Map.of("amount", 200),
            "2026-08-13T10:00:02Z"));

    mockMvc.perform(get("/api/executions/run-tx/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].transactionName").value("second"))
            .andExpect(jsonPath("$[0].input.amount").value(200))
            .andExpect(jsonPath("$[0].executedAt").value("2026-08-13T10:00:02Z"))
            .andExpect(jsonPath("$[1].transactionName").value("first"))
            .andExpect(jsonPath("$[1].input.amount").value(100))
            .andExpect(jsonPath("$[1].executedAt").value("2026-08-13T10:00:01Z"));
  }

  @Test
  void transactionsLiteralRouteIsNotCapturedAsDetailId() throws Exception {
    repository.save(run("transactions", "LoanDisbursement", "COMPLETED",
            "2026-08-13T10:00:00Z", "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions/transactions/transactions"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void invalidLimitReturns400NamingTheParameterAndValue() throws Exception {
    mockMvc.perform(get("/api/executions").param("limit", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message")
                    .value("Invalid value for query parameter 'limit': 'abc' (expected an integer)"));
  }

  @Test
  void invalidOffsetReturns400NamingTheParameterAndValue() throws Exception {
    mockMvc.perform(get("/api/executions").param("offset", "xyz"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message")
                    .value("Invalid value for query parameter 'offset': 'xyz' (expected an integer)"));
  }

  @Test
  void invalidTopProcessesReturns400NamingTheParameterAndValue() throws Exception {
    mockMvc.perform(get("/api/executions/stats").param("topProcesses", "NaN"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message")
                    .value("Invalid value for query parameter 'topProcesses': 'NaN' (expected an integer)"));
  }

  @Test
  void limitAboveMaxIsClampedAndReturns200() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));
    repository.save(run("run-2", "LoanDisbursement", "COMPLETED", "2026-08-13T10:01:00Z",
            "2026-08-13T10:01:05Z", "RUN"));

    mockMvc.perform(get("/api/executions").param("limit", "9999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items.length()").value(2));
  }

  @Test
  void blankNumericParamsFallBackToDefaults() throws Exception {
    repository.save(run("run-1", "LoanDisbursement", "COMPLETED", "2026-08-13T10:00:00Z",
            "2026-08-13T10:00:05Z", "RUN"));

    mockMvc.perform(get("/api/executions")
            .param("limit", "   ")
            .param("offset", "	"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items.length()").value(1));
  }

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode) {
    return run(id, processName, status, startedAt, finishedAt, mode, null, null, null);
  }

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode, String input, String output, String error) {
    return run(id, processName, status, startedAt, finishedAt, mode, input, output, error, null);
  }

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode, String input, String output, String error,
          String correlationId) {
    return DslRun.builder()
            .runId(id)
            .processName(processName)
            .status(status)
            .startedAt(Instant.parse(startedAt))
            .finishedAt(finishedAt != null ? Instant.parse(finishedAt) : null)
            .executionMode(mode)
            .input(input)
            .output(output)
            .error(error)
            .correlationId(correlationId)
            .build();
  }

  private DslRun runWithCorrelationId(String id, String processName, String status,
          String startedAt, String finishedAt, String mode, String correlationId) {
    return run(id, processName, status, startedAt, finishedAt, mode, null, null, null,
            correlationId);
  }

  private DslRun runWithContext(String id, String processName, String status, String startedAt,
          String finishedAt, String mode, String contextJson) {
    return DslRun.builder()
            .runId(id)
            .processName(processName)
            .status(status)
            .startedAt(Instant.parse(startedAt))
            .finishedAt(finishedAt != null ? Instant.parse(finishedAt) : null)
            .executionMode(mode)
            .contextJson(contextJson)
            .build();
  }

  private TransactionExecution tx(String runId, String name, Object input, String executedAt) {
    return new TransactionExecution(runId, name, input, Instant.parse(executedAt));
  }
}
