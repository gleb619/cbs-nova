package cbs.nova.starter.sse;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.model.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequiredArgsConstructor
@Tag(name = "DSL Executions", description = "Execution status Server-Sent Events")
public class ExecutionSseController {

  private final ExecutionSseService sseService;
  private final DslRunRepository runRepository;

  @Operation(summary = "Stream status events for a single execution run")
  @ApiResponse(responseCode = "200", description = "SSE stream of execution status changes", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
  @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping(value = "/api/executions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> events(
          @Parameter(description = "Run id") @PathVariable("id") @NonNull String id) {
    if (runRepository.findByRunId(id).isEmpty()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .contentType(MediaType.APPLICATION_JSON)
              .build();
    }
    SseEmitter emitter = sseService.subscribe(id);
    return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
  }
}
