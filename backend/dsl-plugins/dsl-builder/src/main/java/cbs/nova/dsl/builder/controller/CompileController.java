package cbs.nova.dsl.builder.controller;

import cbs.nova.dsl.BuilderErrorResponse;
import cbs.nova.dsl.builder.exception.BuilderBusyException;
import cbs.nova.dsl.builder.model.CompileModels.CompileRequest;
import cbs.nova.dsl.builder.model.CompileModels.CompileResult;
import cbs.nova.dsl.builder.exception.CompileException;
import cbs.nova.dsl.builder.service.CompileService;
import cbs.nova.dsl.builder.model.CompileSession;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/dsl/compile")
public class CompileController {

  private final CompileService compileService;

  @PostMapping
  public CompileResult compile(@Valid @RequestBody CompileRequest request) {
    return compileService.compile(request);
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
    var session = compileService.findSession(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Unknown compile session: " + id));
    var body = zipBody(session);
    var headers = new HttpHeaders();
    headers.setContentType(MediaType.parseMediaType("application/zip"));
    headers.setContentDispositionFormData("attachment", "dsl-output-" + id + ".zip");
    return ResponseEntity.ok().headers(headers).body(body);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public BuilderErrorResponse handleBadRequest(IllegalArgumentException ex) {
    return of("INVALID_REQUEST", messageOf(ex));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public BuilderErrorResponse handleValidation(MethodArgumentNotValidException ex) {
    var details = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
    var diagnostics = details.isEmpty() ? List.of("Invalid request body") : details;
    return new BuilderErrorResponse(null, null, "VALIDATION_FAILED", diagnostics);
  }

  @ExceptionHandler(CompileException.class)
  @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
  public BuilderErrorResponse handleCompileFailure(CompileException ex) {
    return new BuilderErrorResponse(null, null, "COMPILE_FAILED", ex.getDiagnostics());
  }

  @ExceptionHandler(BuilderBusyException.class)
  @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
  public BuilderErrorResponse handleBusy(BuilderBusyException ex) {
    return of("BUILDER_BUSY", messageOf(ex));
  }

  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<BuilderErrorResponse> handleStatus(ResponseStatusException ex) {
    var body = of("REQUEST_FAILED",
            ex.getReason() != null ? ex.getReason() : messageOf(ex));
    return ResponseEntity.status(ex.getStatusCode()).body(body);
  }

  @ExceptionHandler(Exception.class)
  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
  public BuilderErrorResponse handleUnexpected(Exception ex) {
    return of("INTERNAL_ERROR", messageOf(ex));
  }

  private static BuilderErrorResponse of(String error, String message) {
    return new BuilderErrorResponse(null, null, error, List.of(message));
  }

  private String messageOf(Exception ex) {
    return ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
  }

  private StreamingResponseBody zipBody(CompileSession session) {
    return out -> {
      try {
        session.writeZip(out);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    };
  }
}
