package cbs.nova.starter.controller;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.DslRunSearchResult;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ExecutionDto;
import cbs.nova.starter.model.ExecutionListResponse;
import cbs.nova.starter.model.ExecutionStatsResponse;
import cbs.nova.starter.model.ExecutionTimeseriesResponse;
import cbs.nova.starter.model.TransactionExecutionDto;
import cbs.nova.starter.persistence.DslRunStats;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import cbs.nova.starter.persistence.RunTimeseriesBucket;
import cbs.nova.starter.service.DslRunCancellationService;
import cbs.nova.starter.service.ExecutionCsvWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.StringWriter;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
  static final int CSV_EXPORT_MAX_ROWS = 50_000;
  private static final String CSV_FILENAME_PATTERN = "yyyyMMdd-HHmmss";
  private static final DateTimeFormatter CSV_FILENAME_FORMATTER = DateTimeFormatter
          .ofPattern(CSV_FILENAME_PATTERN);
  private static final int STATS_WINDOW_HOURS = 24;
  private static final int DEFAULT_TOP_PROCESSES = 5;
  private static final int MAX_TOP_PROCESSES = 20;
  private static final int TIMESERIES_DEFAULT_WINDOW_HOURS = 24;
  private static final int TIMESERIES_DEFAULT_BUCKET_MINUTES = 60;
  private static final int TIMESERIES_MIN_WINDOW_HOURS = 1;
  private static final int TIMESERIES_MAX_WINDOW_HOURS = 24 * 30;
  private static final int TIMESERIES_MIN_BUCKET_MINUTES = 1;
  private static final int TIMESERIES_MAX_BUCKET_MINUTES = 60 * 24;

  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final DslRunCancellationService cancellationService;
  private final @Nullable DslRunStatsRepository statsRepository;
  private final TransactionExecutionRepository transactionExecutionRepository;

  @Operation(summary = "List DSL execution runs")
  @ApiResponse(responseCode = "200", description = "Matching execution runs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionListResponse.class)))
  public ServerResponse list(ServerRequest request) throws IOException {
    ExecutionFilters filters = readFilters(request);
    int limit = intParam(request, "limit", 50);
    int offset = intParam(request, "offset", 0);
    int pageSize = clampLimit(limit);
    int skip = clampOffset(offset);
    DslRunSearchResult result = runRepository.search(filters.processName(), filters.status(),
            filters.mode(), filters.correlationId(), skip, pageSize);
    List<ExecutionDto> items = result.items().stream()
            .map(ExecutionDto::from)
            .toList();
    return ServerResponse.ok().body(new ExecutionListResponse(items, result.total()));
  }

  @Operation(summary = "Export DSL execution runs as CSV")
  @ApiResponse(responseCode = "200", description = "CSV export of matching execution runs", content = @Content(mediaType = "text/csv"))
  public ServerResponse exportCsv(ServerRequest request) throws IOException {
    ExecutionFilters filters = readFilters(request);
    DslRunSearchResult result = runRepository.search(filters.processName(), filters.status(),
            filters.mode(), filters.correlationId(), 0, CSV_EXPORT_MAX_ROWS + 1);
    boolean truncated = result.items().size() > CSV_EXPORT_MAX_ROWS;
    List<DslRun> runs = truncated
            ? result.items().subList(0, CSV_EXPORT_MAX_ROWS)
            : result.items();
    StringWriter writer = new StringWriter();
    new ExecutionCsvWriter().writeCsv(runs, writer);
    String filename = "executions-" + CSV_FILENAME_FORMATTER.format(LocalDateTime.now()) + ".csv";
    ServerResponse.BodyBuilder response = ServerResponse.ok()
            .contentType(
                    MediaType.parseMediaType("text/csv; charset=utf-8"))
            .header("Content-Disposition", "attachment; filename=\"" + filename + "\"");
    if (truncated) {
      response = response.header("X-Export-Truncated", "true");
    }
    return response.body(writer.toString());
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
            intParam(request, "topProcesses", DEFAULT_TOP_PROCESSES));
    Instant windowStart = Instant.now().minus(Duration.ofHours(STATS_WINDOW_HOURS));

    DslRunStats stats = statsRepository != null
            ? statsRepository.stats(windowStart, topProcesses)
            : scanStats(windowStart, topProcesses);
    return ServerResponse.ok().body(ExecutionStatsResponse.from(stats, STATS_WINDOW_HOURS));
  }

  /**
   * Per-bucket run counts grouped by status for the dashboard trend chart.
   *
   * <p>
   * Like {@link #stats}, the SQL aggregate (when the repo supports it) is preferred; otherwise the
   * handler falls back to scanning the repository. Either way the response is zero-filled into a
   * uniform (bucket, status) grid so the frontend x-axis never goes sparse.
   *
   * <p>
   * Params are clamped to safe ranges; non-numeric values yield 400 via the existing
   * {@link IllegalArgumentException} mapping.
   */
  @Operation(summary = "Per-bucket run counts grouped by status")
  @ApiResponse(responseCode = "200", description = "Per-bucket run counts", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionTimeseriesResponse.class)))
  public ServerResponse timeseries(ServerRequest request) {
    int windowHours = clampWindowHours(
            intParam(request, "windowHours", TIMESERIES_DEFAULT_WINDOW_HOURS));
    int bucketMinutes = clampBucketMinutes(
            intParam(request, "bucketMinutes", TIMESERIES_DEFAULT_BUCKET_MINUTES), windowHours);

    Instant windowEnd = Instant.now();
    Instant windowStart = windowEnd.minus(Duration.ofHours(windowHours));
    Duration bucketSize = Duration.ofMinutes(bucketMinutes);

    List<RunTimeseriesBucket> rows = statsRepository != null
            ? statsRepository.timeseries(windowStart, windowEnd, bucketSize)
            : scanTimeseries(windowStart, windowEnd, bucketSize);

    return ServerResponse.ok().body(
            ExecutionTimeseriesResponse.from(rows, windowStart, windowEnd, bucketSize));
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
   * Returns the transaction executions recorded for a run.
   *
   * <p>
   * The response is a bare JSON array (not a wrapper object) to keep the sub-resource lightweight.
   * Rows are returned in repository order, which is newest-first for both the JDBC and in-memory
   * stores.
   */
  @Operation(summary = "List transaction executions for a run")
  @ApiResponse(responseCode = "200", description = "Transaction executions for the run, most recent first", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TransactionExecutionDto.class))))
  @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse transactions(ServerRequest request) {
    String id = request.pathVariable("id");
    if (runRepository.findByRunId(id).isEmpty()) {
      return ServerResponse.status(HttpStatus.NOT_FOUND)
              .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + id,
                      null, id, null));
    }
    List<TransactionExecutionDto> transactions = transactionExecutionRepository.findByRunId(id)
            .stream()
            .map(this::toDto)
            .toList();
    return ServerResponse.ok().body(transactions);
  }

  private TransactionExecutionDto toDto(TransactionExecution execution) {
    return new TransactionExecutionDto(
            execution.transactionName(),
            execution.input(),
            execution.executedAt().toString());
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

  private static ExecutionFilters readFilters(ServerRequest request) {
    String processName = request.param("processName").filter(s -> !s.isBlank()).orElse(null);
    String status = request.param("status").orElse(null);
    String mode = request.param("mode").orElse(null);
    String correlationId = request.param("correlationId")
            .map(String::trim)
            .filter(s -> !s.isBlank())
            .orElse(null);
    return new ExecutionFilters(processName, status, mode, correlationId);
  }

  private record ExecutionFilters(String processName, String status, String mode,
          String correlationId) {
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

  private static int clampWindowHours(int windowHours) {
    return Math.max(TIMESERIES_MIN_WINDOW_HOURS,
            Math.min(windowHours, TIMESERIES_MAX_WINDOW_HOURS));
  }

  private static int clampBucketMinutes(int bucketMinutes, int windowHours) {
    int clamped = Math.max(TIMESERIES_MIN_BUCKET_MINUTES,
            Math.min(bucketMinutes, TIMESERIES_MAX_BUCKET_MINUTES));
    int maxBucketMinutes = Math.max(TIMESERIES_MIN_BUCKET_MINUTES, windowHours * 60);
    if (clamped > maxBucketMinutes) {
      clamped = maxBucketMinutes;
    }
    // Ensure the bucket width divides the window evenly so the JDBC aggregate
    // (and the in-memory fallback) can produce a stable, zero-filled grid.
    while (clamped > TIMESERIES_MIN_BUCKET_MINUTES
            && (windowHours * 60) % clamped != 0) {
      clamped--;
    }
    return clamped;
  }

  private static int intParam(ServerRequest request, String name, int defaultValue) {
    var raw = request.param(name).filter(s -> !s.isBlank()).orElse(null);
    if (raw == null) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(raw.trim());
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException(
              "Invalid value for query parameter '" + name + "': '" + raw
                      + "' (expected an integer)");
    }
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

  /**
   * Fallback bucketing when the store cannot aggregate server-side. Scans every run (no
   * {@code MAX_LIMIT} clamp) and folds into the requested bucket width; the store-level query
   * returns only buckets that have rows, but the handler's response factory zero-fills empties.
   */
  private List<RunTimeseriesBucket> scanTimeseries(Instant windowStart, Instant windowEnd,
          Duration bucketSize) {
    long bucketSeconds = bucketSize.getSeconds();
    Map<String, Map<Long, Long>> byStatusByBucket = new LinkedHashMap<>();
    for (DslRun run : findRuns(null)) {
      Instant startedAt = run.startedAt();
      if (startedAt.isBefore(windowStart) || !startedAt.isBefore(windowEnd)) {
        continue;
      }
      long secondsFromStart = Duration.between(windowStart, startedAt).getSeconds();
      long bucketIndex = secondsFromStart / bucketSeconds;
      byStatusByBucket
              .computeIfAbsent(run.status(), k -> new LinkedHashMap<>())
              .merge(bucketIndex, 1L, Long::sum);
    }
    List<RunTimeseriesBucket> out = new ArrayList<>();
    for (var statusEntry : byStatusByBucket.entrySet()) {
      for (var bucketEntry : statusEntry.getValue().entrySet()) {
        Instant bucketStart = windowStart.plusSeconds(bucketEntry.getKey() * bucketSeconds);
        out.add(new RunTimeseriesBucket(bucketStart, statusEntry.getKey(), bucketEntry.getValue()));
      }
    }
    out.sort(Comparator.comparing(RunTimeseriesBucket::bucketStart)
            .thenComparing(RunTimeseriesBucket::status));
    return out;
  }
}
