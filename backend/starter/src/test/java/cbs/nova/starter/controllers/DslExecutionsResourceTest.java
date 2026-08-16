package cbs.nova.starter.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

import java.time.Instant;

class DslExecutionsResourceTest {

  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
            .standaloneSetup(new DslExecutionsResource(repository, objectMapper))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
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

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode) {
    return run(id, processName, status, startedAt, finishedAt, mode, null, null, null);
  }

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode, String input, String output, String error) {
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
            .build();
  }
}
