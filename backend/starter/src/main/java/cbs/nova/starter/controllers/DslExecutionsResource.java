package cbs.nova.starter.controllers;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.models.ErrorResponse;
import cbs.nova.starter.models.ExecutionDto;
import cbs.nova.starter.models.ExecutionListResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * Read-only REST surface for DSL execution runs backing the frontend Executions page.
 *
 * <p>
 * The {@link DslRunRepository} interface exposes no dedicated "list all" method, so unfiltered
 * listings are assembled by enumerating {@code knownProcessNames()} and {@code findByProcessName} —
 * the same pattern {@code TemporalDslProcessService} uses for its staleness sweep. Status filtering
 * is applied in the controller against the raw backend enum name (e.g. {@code STALE}), before the
 * run is mapped to the frontend-facing {@link ExecutionDto}.
 */
@RestController
@RequestMapping("/api/executions")
@Tag(name = "DSL Executions", description = "Inspect DSL execution runs")
@RequiredArgsConstructor
public class DslExecutionsResource {

  private static final int MAX_LIMIT = 500;

  private final DslRunRepository runRepository;
  private final ObjectMapper objectMapper;

  @GetMapping
  @Operation(summary = "List DSL execution runs")
  @ApiResponse(responseCode = "200", description = "Matching execution runs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionListResponse.class)))
  public ResponseEntity<ExecutionListResponse> list(
          @RequestParam(name = "processName", required = false) String processName,
          @RequestParam(name = "status", required = false) String status,
          @RequestParam(name = "limit", defaultValue = "50") int limit) {
    int pageSize = clampLimit(limit);
    List<DslRun> filtered = findRuns(processName).stream()
            .filter(run -> status == null || status.equals(run.status()))
            .toList();
    int total = filtered.size();
    List<ExecutionDto> items = filtered.stream()
            .limit(pageSize)
            .map(ExecutionDto::from)
            .toList();
    return ResponseEntity.ok(new ExecutionListResponse(items, total));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get a single DSL execution run by id")
  @ApiResponse(responseCode = "200", description = "The execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class)))
  @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ResponseEntity<?> detail(@PathVariable String id) {
    return runRepository.findByRunId(id)
            .<ResponseEntity<?>>map(
                    run -> ResponseEntity.ok(ExecutionDto.fromDetail(run, objectMapper)))
            .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + id, null,
                            id, null)));
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
}
