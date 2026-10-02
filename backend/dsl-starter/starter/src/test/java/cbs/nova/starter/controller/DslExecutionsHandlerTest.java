package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.config.router.DslExecutionsRouterConfiguration;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.persistence.DslRunStats;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import cbs.nova.starter.persistence.RunTimeseriesBucket;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslRunCancellationService;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link DslExecutionsHandler}.
 *
 * <p>
 * Deviation from the plan file: {@link cbs.nova.starter.service.ExecutionCsvWriter} is instantiated
 * inside {@link #exportCsv}, so it cannot be mocked; the CSV export test exercises the real writer
 * and asserts the row-cap truncation logic instead.
 */
class DslExecutionsHandlerTest {

  private static final String RUN_ID = "run-1";
  private static final String PROCESS = "demo-process";

  private final RequestQueryConverter queryConverter = new RequestQueryConverter();
  private final ObjectMapper objectMapper = new ObjectMapper();

  private Harness harness() {
    DslRunRepository runRepository = mock(DslRunRepository.class);
    TransactionExecutionRepository transactionExecutionRepository = mock(
            TransactionExecutionRepository.class);
    DslRunStatsRepository statsRepository = mock(DslRunStatsRepository.class);
    DslRunCancellationService cancellationService = mock(DslRunCancellationService.class);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> auditProvider = mock(ObjectProvider.class);

    DslExecutionsHandler handler = new DslExecutionsHandler(
            runRepository,
            objectMapper,
            cancellationService,
            statsRepository,
            transactionExecutionRepository,
            queryConverter,
            auditProvider);

    MockMvc mockMvc = MockMvcBuilders.routerFunctions(
            new DslExecutionsRouterConfiguration().dslExecutionsRouter(handler,
                    new cbs.nova.starter.web.RequestBodyLimitFilter(
                            new cbs.nova.starter.config.properties.CbsNovaWebProperties(0L))))
            .build();

    return new Harness(runRepository, transactionExecutionRepository, statsRepository,
            cancellationService, auditProvider, mockMvc);
  }

  private record Harness(
          DslRunRepository runRepository,
          TransactionExecutionRepository transactionExecutionRepository,
          DslRunStatsRepository statsRepository,
          DslRunCancellationService cancellationService,
          ObjectProvider<DslAuditService> auditProvider,
          MockMvc mockMvc) {
  }

  @Test
  void listAppliesFiltersAndPagination() throws Exception {
    Harness h = harness();
    DslRun run = run(RUN_ID, PROCESS, DslRunStatus.RUNNING.name(), Instant.now(), null);
    DslRunSearchResult result = new DslRunSearchResult(List.of(run), 1);
    when(h.runRepository.search(eq(PROCESS), eq("RUNNING"), eq("sync"), eq("corr"), eq(5),
            eq(10))).thenReturn(result);

    h.mockMvc.perform(get("/api/executions")
            .param("processName", PROCESS)
            .param("status", "RUNNING")
            .param("mode", "sync")
            .param("correlationId", "corr")
            .param("limit", "10")
            .param("offset", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.offset").value(5))
            .andExpect(jsonPath("$.limit").value(10))
            .andExpect(jsonPath("$.items[0].id").value(RUN_ID));
  }

  @Test
  void statsClampsTopProcessesAndReturnsAggregates() throws Exception {
    Harness h = harness();
    Map<String, Long> counts = new LinkedHashMap<>();
    counts.put(DslRunStatus.RUNNING.name(), 3L);
    DslRunStats stats = new DslRunStats(5L, counts, 2L, 1L, 0.5,
            List.of(new DslRunStats.ProcessRunCount(PROCESS, 5L)));
    when(h.statsRepository.stats(any(Instant.class), eq(StarterConstants.MAX_TOP_PROCESSES)))
            .thenReturn(stats);

    h.mockMvc.perform(get("/api/executions/stats").param("topProcesses", "100"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalRuns").value(5))
            .andExpect(jsonPath("$.windowRuns").value(2))
            .andExpect(jsonPath("$.windowHours").value(StarterConstants.STATS_WINDOW_HOURS))
            .andExpect(jsonPath("$.topProcesses[0].processName").value(PROCESS));
  }

  @Test
  void timeseriesClampsWindowAndBucket() throws Exception {
    Harness h = harness();
    when(h.statsRepository.timeseries(any(Instant.class), any(Instant.class),
            any(Duration.class))).thenReturn(List.of());

    h.mockMvc.perform(get("/api/executions/stats/timeseries")
            .param("windowHours", "1000")
            .param("bucketMinutes", "2000"))
            .andExpect(status().isOk());

    ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
    ArgumentCaptor<Duration> bucketCaptor = ArgumentCaptor.forClass(Duration.class);
    verify(h.statsRepository).timeseries(startCaptor.capture(), endCaptor.capture(),
            bucketCaptor.capture());

    Instant windowEnd = endCaptor.getValue();
    Instant windowStart = startCaptor.getValue();
    Duration bucket = bucketCaptor.getValue();

    assertThat(windowEnd).isAfter(Instant.now().minusSeconds(5));
    assertThat(windowStart).isEqualTo(
            windowEnd.minus(Duration.ofHours(StarterConstants.TIMESERIES_MAX_WINDOW_HOURS)));
    assertThat(bucket)
            .isEqualTo(Duration.ofMinutes(StarterConstants.TIMESERIES_MAX_BUCKET_MINUTES));
  }

  @Test
  void cancelReturnsOkWhenCancelled() throws Exception {
    Harness h = harness();
    DslRun run = run(RUN_ID, PROCESS, DslRunStatus.RUNNING.name(), Instant.now(), null);
    when(h.cancellationService.cancel(RUN_ID)).thenReturn(
            new DslRunCancellationService.CancelResult(
                    DslRunCancellationService.Outcome.CANCELLED, run,
                    DslRunStatus.RUNNING.name()));

    h.mockMvc.perform(post("/api/executions/{id}/cancel", RUN_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(RUN_ID));
  }

  @Test
  void cancelReturnsNotFound() throws Exception {
    Harness h = harness();
    when(h.cancellationService.cancel(RUN_ID)).thenReturn(
            new DslRunCancellationService.CancelResult(
                    DslRunCancellationService.Outcome.NOT_FOUND, null, null));

    h.mockMvc.perform(post("/api/executions/{id}/cancel", RUN_ID))
            .andExpect(status().isNotFound());
  }

  @Test
  void cancelReturnsConflictWhenNotCancellable() throws Exception {
    Harness h = harness();
    DslRun run = run(RUN_ID, PROCESS, DslRunStatus.COMPLETED.name(), Instant.now(), Instant.now());
    when(h.cancellationService.cancel(RUN_ID)).thenReturn(
            new DslRunCancellationService.CancelResult(
                    DslRunCancellationService.Outcome.NOT_CANCELLABLE, run,
                    DslRunStatus.COMPLETED.name()));

    h.mockMvc.perform(post("/api/executions/{id}/cancel", RUN_ID))
            .andExpect(status().isConflict());
  }

  @Test
  void csvExportTruncatesAtMaxRows() throws Exception {
    Harness h = harness();
    DslRun run = run("r-2", PROCESS, DslRunStatus.COMPLETED.name(),
            Instant.now().minusSeconds(10), Instant.now());
    List<DslRun> rows = Collections.nCopies(StarterConstants.CSV_EXPORT_MAX_ROWS + 1, run);
    DslRunSearchResult result = new DslRunSearchResult(rows, rows.size());
    when(h.runRepository.search(eq(PROCESS), eq(null), eq(null), eq(null), eq(0),
            eq(StarterConstants.CSV_EXPORT_MAX_ROWS + 1))).thenReturn(result);

    MvcResult mvcResult = h.mockMvc.perform(get("/api/executions/export.csv")
            .param("processName", PROCESS))
            .andExpect(status().isOk())
            .andExpect(header().string("X-Export-Truncated", "true"))
            .andExpect(content().contentType("text/csv;charset=UTF-8"))
            .andReturn();

    String body = mvcResult.getResponse().getContentAsString();
    assertThat(body.lines().count()).isEqualTo(StarterConstants.CSV_EXPORT_MAX_ROWS + 1L);
  }

  @Test
  void detailReturnsRunWhenFound() throws Exception {
    Harness h = harness();
    DslRun run = run(RUN_ID, PROCESS, DslRunStatus.RUNNING.name(), Instant.now(), null);
    when(h.runRepository.findByRunId(RUN_ID)).thenReturn(Optional.of(run));

    h.mockMvc.perform(get("/api/executions/{id}", RUN_ID))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(RUN_ID))
            .andExpect(jsonPath("$.status").value(equalTo("Running")));
  }

  @Test
  void detailReturnsNotFoundWhenMissing() throws Exception {
    Harness h = harness();
    when(h.runRepository.findByRunId(RUN_ID)).thenReturn(Optional.empty());

    h.mockMvc.perform(get("/api/executions/{id}", RUN_ID))
            .andExpect(status().isNotFound());
  }

  @Test
  void transactionsReturnsNotFoundWhenRunMissing() throws Exception {
    Harness h = harness();
    when(h.runRepository.findByRunId(RUN_ID)).thenReturn(Optional.empty());

    h.mockMvc.perform(get("/api/executions/{id}/transactions", RUN_ID))
            .andExpect(status().isNotFound());
  }

  private static DslRun run(String runId, String processName, String status, Instant startedAt,
          Instant finishedAt) {
    return DslRun.builder()
            .runId(runId)
            .processName(processName)
            .status(status)
            .startedAt(startedAt)
            .finishedAt(finishedAt)
            .build();
  }
}
