package cbs.nova.starter.controller;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ExecutionDto;
import cbs.nova.starter.model.ExecutionListResponse;
import cbs.nova.starter.model.ExecutionStatsResponse;
import cbs.nova.starter.persistence.DslRunStats;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import cbs.nova.starter.service.DslRunCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Functional handler for the DSL execution runs endpoint. Registered as a {@code RouterFunction}
 * bean by {@link cbs.nova.starter.config.DslExecutionsRouterConfiguration} rather than as a
 * hardcoded {@code @RestController}, following the same pattern as DSL reload and introspection.
 */
@Component
@Tag(name = "DSL Executions", description = "Inspect DSL execution runs")
@RequiredArgsConstructor
// TODO: Add mapstrcut mapper, that map `request.param` to a record
public class DslExecutionsHandler {

  private static final int MAX_LIMIT = 500;
  private static final int STATS_WINDOW_HOURS = 24;
  private static final int DEFAULT_TOP_PROCESSES = 5;
  private static final int MAX_TOP_PROCESSES = 20;

  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final DslRunCancellationService cancellationService;
  private final @Nullable DslRunStatsRepository statsRepository;

  @Operation(summary = "List DSL execution runs")
  @ApiResponse(responseCode = "200", description = "Matching execution runs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionListResponse.class)))
  public ServerResponse list(ServerRequest request) throws IOException {
    String processName = request.param("processName").filter(s -> !s.isBlank()).orElse(null);
    String status = request.param("status").orElse(null);
    String mode = request.param("mode").orElse(null);
    int limit = request.param("limit").map(Integer::parseInt).orElse(50);
    int offset = request.param("offset").map(Integer::parseInt).orElse(0);
    int pageSize = clampLimit(limit);
    int skip = clampOffset(offset);
    DslRunSearchResult result = runRepository.search(processName, status, mode, skip, pageSize);
    List<ExecutionDto> items = result.items().stream()
            .map(ExecutionDto::from)
            .toList();
    return ServerResponse.ok().body(new ExecutionListResponse(items, result.total()));
  }

  /**
   * Aggregate statistics for the dashboard.
   *
   * <p>
   * Unlike {@link #list}, counts are never paginated or clamped by {@code MAX_LIMIT}: when the
   * repository can aggregate server-side ({@link DslRunStatsRepository}, the JDBC store) the
   * numbers come from SQL {@code COUNT}/{@code GROUP BY}; otherwise the handler scans the
   * repository's full (unpaginated) contents, which stays exact for in-memory stores. Either way
   * the dashboard cannot miscount the way the old client-side approach did over clamped list pages.
   *
   * <p>
   * All counters describe whatever rows currently exist — retention purges (T276) simply shrink
   * them, so the dashboard stays correct as old rows disappear.
   */
  @Operation(summary = "Aggregate DSL execution run statistics")
  @ApiResponse(responseCode = "200", description = "Aggregate run statistics", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionStatsResponse.class)))
  public ServerResponse stats(ServerRequest request) {
    int topProcesses = clampTopProcesses(
            request.param("topProcesses").map(Integer::parseInt).orElse(DEFAULT_TOP_PROCESSES));
    Instant windowStart = Instant.now().minus(Duration.ofHours(STATS_WINDOW_HOURS));

    DslRunStats stats = statsRepository != null
            ? statsRepository.stats(windowStart, topProcesses)
            : scanStats(windowStart, topProcesses);
    return ServerResponse.ok().body(ExecutionStatsResponse.from(stats, STATS_WINDOW_HOURS));
  }

  @Operation(summary = "Get a single DSL execution run by id")
  @ApiResponse(responseCode = "200", description = "The execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class)))
  @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse detail(ServerRequest request) throws IOException {
    String id = request.pathVariable("id");
    return runRepository.findByRunId(id)
            .map(run -> ServerResponse.ok().body(ExecutionDto.fromDetail(run, objectMapper)))
            .orElse(ServerResponse.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + id,
                            null, id, null)));
  }

  /**
   * Cancel a RUNNING execution run.
   *
   * <p>
   * Sibling of {@link #list} / {@link #detail}: cancellation is an operation on an execution run,
   * so it lives on the same handler and the same {@code /api/executions/{id}} path family.
   *
   * <p>
   * A distinct {@code CANCELLED} status is used rather than reusing {@code STALE}: STALE means "we
   * lost track of this run", which is a diagnostic signal, whereas CANCELLED is a deliberate
   * operator action. Conflating them would make the healthcheck sweep's output unreadable and would
   * hide accidental cancellations.
   *
   * <p>
   * The 409 case is not a separate pre-check: {@link DslRunCancellationService} performs the
   * terminal write through the guarded compare-and-set update, so a run that reaches COMPLETED or
   * FAILED between our read and our write yields zero affected rows and is reported here as a
   * conflict instead of being clobbered to CANCELLED.
   */
  @Operation(summary = "Cancel a running DSL execution run")
  @ApiResponse(responseCode = "200", description = "The cancelled execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class)))
  @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "The run is not in a cancellable state", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse cancel(ServerRequest request) {
    String id = request.pathVariable("id");
    DslRunCancellationService.CancelResult result = cancellationService.cancel(id);
    return switch (result.outcome()) {
      case NOT_FOUND -> ServerResponse.status(HttpStatus.NOT_FOUND)
              .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + id,
                      null, id, null));
      case NOT_CANCELLABLE -> ServerResponse.status(HttpStatus.CONFLICT)
              .body(new ErrorResponse("CONFLICT",
                      "Execution run is not cancellable: " + id + " (status "
                              + result.currentStatus() + ")",
                      null, id, null));
      case CANCELLED -> ServerResponse.ok()
              .body(ExecutionDto.fromDetail(requireRun(result, id), objectMapper));
    };
  }

  private static DslRun requireRun(DslRunCancellationService.CancelResult result, String id) {
    DslRun run = result.run();
    if (run == null) {
      throw new IllegalStateException("Cancelled run missing from repository: " + id);
    }
    return run;
  }

  private List<DslRun> findRuns(String processName) {
    if (processName != null && !processName.isBlank()) {
      return runRepository.findByProcessName(processName);
    }
    return runRepository.knownProcessNames().stream()
            .flatMap(name -> runRepository.findByProcessName(name).stream())
            .toList();
  }

  private static int clampLimit(int limit) {
    return Math.max(1, Math.min(limit, MAX_LIMIT));
  }

  private static int clampOffset(int offset) {
    return Math.max(0, offset);
  }

  private static int clampTopProcesses(int topProcesses) {
    return Math.max(1, Math.min(topProcesses, MAX_TOP_PROCESSES));
  }

  /**
   * Fallback aggregation over the repository's full contents, used when the store cannot aggregate
   * server-side. Scans every run (unlike {@link #list}, no {@code MAX_LIMIT} clamp), so the counts
   * are exact for in-memory repositories.
   */
  private DslRunStats scanStats(Instant windowStart, int topProcessesLimit) {
    List<DslRun> allRuns = findRuns(null);
    Map<String, Long> statusCounts = new LinkedHashMap<>();
    Map<String, Long> processCounts = new LinkedHashMap<>();
    long windowRuns = 0;
    long windowFailedRuns = 0;
    for (DslRun run : allRuns) {
      statusCounts.merge(run.status(), 1L, Long::sum);
      processCounts.merge(run.processName(), 1L, Long::sum);
      if (!run.startedAt().isBefore(windowStart)) {
        windowRuns++;
        if (DslRunStatus.FAILED.name().equals(run.status())) {
          windowFailedRuns++;
        }
      }
    }
    List<DslRunStats.ProcessRunCount> topProcessesList = processCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                    .thenComparing(Map.Entry.comparingByKey()))
            .limit(topProcessesLimit)
            .map(e -> new DslRunStats.ProcessRunCount(e.getKey(), e.getValue()))
            .toList();
    double failureRate = windowRuns == 0 ? 0.0 : (double) windowFailedRuns / windowRuns;
    return new DslRunStats(allRuns.size(), statusCounts, windowRuns, windowFailedRuns, failureRate,
            topProcessesList);
  }
}
