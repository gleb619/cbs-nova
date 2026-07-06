package cbs.nova.starter;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DslException;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.jspecify.annotations.NonNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/dsl")
@Tag(name = "DSL Runtime", description = "Execute DSL processes and transactions")
public class DslRuntimeResource {

  private final DslRuntime dslRuntime;

  public DslRuntimeResource(@NonNull DslRuntime dslRuntime) {
    this.dslRuntime = dslRuntime;
  }

  @PostMapping("/preview/{name}")
  @Operation(summary = "Preview a DSL process without side effects")
  public ResponseEntity<?> preview(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.PREVIEW);
    Result<PreviewReport> result = dslRuntime.preview(name, ctx);
    return result.isSuccess()
            ? ResponseEntity.ok(result.value())
            : ResponseEntity.unprocessableEntity()
                    .body(toErrorResponse(name, ctx, result.cause()));
  }

  @PostMapping("/run/{name}")
  @Operation(summary = "Execute a DSL process with full side effects")
  public ResponseEntity<?> run(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.RUN);
    Result<?> result = dslRuntime.run(name, ctx);
    return result.isSuccess()
            ? ResponseEntity.ok(result.value())
            : ResponseEntity.unprocessableEntity()
                    .body(toErrorResponse(name, ctx, result.cause()));
  }

  @PostMapping("/explain/{name}")
  @Operation(summary = "Return a static analysis report of a DSL process")
  public ResponseEntity<ExplainReport> explain(
          @PathVariable String name, @RequestBody DslRequest request) {
    var ctx = toContext(request, ExecutionMode.EXPLAIN);
    ExplainReport report = dslRuntime.explain(name, ctx);
    return ResponseEntity.ok(report);
  }

  private Context<?> toContext(DslRequest request, ExecutionMode mode) {
    Map<String, Object> metadata = request.metadata() != null ? request.metadata() : Map.of();
    return SimpleContext.of(request.body(), metadata, mode, SimpleContext.generateRunId());
  }

  private ErrorResponse toErrorResponse(String entityName, Context<?> ctx, Throwable cause) {
    if (cause instanceof DslException d) {
      return new ErrorResponse(d.code().name(), d.getMessage(), entityName, d.runId(),
              d.exceptionId());
    }
    String runId = ctx.runId();
    String exceptionId = runId + ":ex:" + UUID.randomUUID();
    String message = cause.getMessage() != null
            ? cause.getMessage()
            : cause.getClass().getSimpleName();
    return new ErrorResponse("EXECUTION_FAILED", message, entityName, runId, exceptionId);
  }

  public record DslRequest(Object body, Map<String, Object> metadata) {
  }
}
