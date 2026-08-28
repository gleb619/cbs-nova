package cbs.nova.starter.controller;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ExecutionDto;
import cbs.nova.starter.model.ExecutionListResponse;
import cbs.nova.starter.service.DslRunCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

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

  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;
  private final DslRunCancellationService cancellationService;

  @Operation(summary = "List DSL execution runs")
  @ApiResponse(responseCode = "200", description = "Matching execution runs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionListResponse.class)))
  public ServerResponse list(ServerRequest request) throws IOException {
    String processName = request.param("processName").orElse(null);
    String status = request.param("status").orElse(null);
    String mode = request.param("mode").orElse(null);
    int limit = request.param("limit").map(Integer::parseInt).orElse(50);
    int offset = request.param("offset").map(Integer::parseInt).orElse(0);
    int pageSize = clampLimit(limit);
    int skip = clampOffset(offset);
    List<DslRun> filtered = findRuns(processName).stream()
            .filter(run -> status == null || status.equalsIgnoreCase(run.status()))
            .filter(run -> mode == null || mode.equalsIgnoreCase(effectiveMode(run)))
            .toList();
    int total = filtered.size();
    List<ExecutionDto> items = filtered.stream()
            .skip(skip)
            .limit(pageSize)
            .map(ExecutionDto::from)
            .toList();
    return ServerResponse.ok().body(new ExecutionListResponse(items, total));
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

  private static String effectiveMode(DslRun run) {
    String stored = run.executionMode();
    return stored == null || stored.isBlank() ? "RUN" : stored.toUpperCase(Locale.ROOT);
  }
}
