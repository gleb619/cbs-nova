package cbs.nova.starter.sse;

import cbs.nova.dsl.model.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Streams live dry-run (preview/explain) log lines for a single trace/run id. The id is the run id
 * the client supplied via {@code X-Request-Id} on the preview/explain request, so the client can
 * open the stream before the (blocking) preview/explain call completes. A malformed trace id is
 * rejected with 404.
 */
@RestController
@RequiredArgsConstructor
@Tag(name = "DSL Dry Run", description = "Dry-run log Server-Sent Events")
public class DryRunLogSseController {

  /**
   * Same shape {@code SimpleContext.generateRunId()} / request ids produce: no slashes, spaces or
   * escapes.
   */
  private static final Pattern TRACE_ID_PATTERN = Pattern
          .compile("^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$");

  private final DryRunLogSseService sseService;

  @Operation(summary = "Stream live dry-run log lines for a single trace/run id")
  @ApiResponse(responseCode = "200", description = "SSE stream of dry-run log events", content = @Content(mediaType = MediaType.TEXT_EVENT_STREAM_VALUE))
  @ApiResponse(responseCode = "404", description = "Malformed trace id", content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE, schema = @Schema(implementation = ErrorResponse.class)))
  @GetMapping(value = "/api/dsl/dry-run/{traceId}/logs", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public ResponseEntity<SseEmitter> logs(
          @Parameter(description = "Trace/run id (X-Request-Id used for the preview/explain call)") @PathVariable("traceId") @NonNull String traceId) {
    if (!TRACE_ID_PATTERN.matcher(traceId).matches()) {
      return ResponseEntity.status(HttpStatus.NOT_FOUND)
              .contentType(MediaType.APPLICATION_JSON)
              .build();
    }
    SseEmitter emitter = sseService.subscribe(traceId);
    return ResponseEntity.ok()
            .contentType(MediaType.TEXT_EVENT_STREAM)
            .body(emitter);
  }
}
