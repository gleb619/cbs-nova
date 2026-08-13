package cbs.nova.starter.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.DslRun;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;

class DslExecutionsResourceTest {

  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders
            .standaloneSetup(new DslExecutionsResource(repository))
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

  private DslRun run(String id, String processName, String status, String startedAt,
          String finishedAt, String mode) {
    return DslRun.builder()
            .runId(id)
            .processName(processName)
            .status(status)
            .startedAt(Instant.parse(startedAt))
            .finishedAt(finishedAt != null ? Instant.parse(finishedAt) : null)
            .executionMode(mode)
            .build();
  }
}
