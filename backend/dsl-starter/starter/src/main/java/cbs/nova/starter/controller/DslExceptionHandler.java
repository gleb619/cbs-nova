package cbs.nova.starter.controller;

import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.converter.DslExceptionMapper;
import cbs.nova.starter.exception.ApiKeyNotFoundException;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.exception.DslSubscriberCapExceededException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.dsl.model.ErrorResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
@RequiredArgsConstructor
public class DslExceptionHandler extends ResponseEntityExceptionHandler {

  private final DslExceptionMapper dslExceptionMapper;

  @Override
  protected @Nullable ResponseEntity<Object> handleExceptionInternal(Exception ex,
          @Nullable Object body,
          HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    log.error("ERROR_INTERNAL: {}", ex.getMessage(), ex);
    return super.handleExceptionInternal(ex, body, headers, statusCode, request);
  }

  @ExceptionHandler(DslPayloadTooLargeException.class)
  public ResponseEntity<ErrorResponse> handlePayloadTooLarge(DslPayloadTooLargeException ex,
          WebRequest request) {
    // T800: client mistake (oversized body) — WARN, no stack. Keeps the operator-visible record
    // but stops every oversized upload from paging on-call via Bugsink.
    log.warn("PAYLOAD_TOO_LARGE: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
            .body(new ErrorResponse("PAYLOAD_TOO_LARGE", ex.getMessage(), ex.getEntityName(), null,
                    null, null, null, null, null));
  }

  @ExceptionHandler(DslSubscriberCapExceededException.class)
  public ResponseEntity<ErrorResponse> handleSubscriberCap(DslSubscriberCapExceededException ex,
          WebRequest request) {
    // T800: client-quota event (429) — WARN, no stack. Expected when many subscribers hit the cap.
    log.warn("SUBSCRIBER_CAP_EXCEEDED: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("SUBSCRIBER_CAP_EXCEEDED", ex.getMessage(), null, null, null,
                    null, null, null, null));
  }

  @ExceptionHandler(DslException.class)
  public ResponseEntity<ErrorResponse> handleDslException(DslException ex, WebRequest request) {
    // T800: 422 is a server-side DSL failure, not a client typo. Keep ERROR+stack so the explicit
    // Sentry.captureException site in DefaultDslExceptionMapper has a matching log event to anchor
    // on when the breadcrumbs surface in Bugsink.
    log.error("DSL_ERROR: {}", ex.getMessage(), ex);
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(DefinitionNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleDefinitionNotFound(DefinitionNotFoundException ex,
          WebRequest request) {
    // T800: 404 for a typo'd definition name — client mistake, WARN no stack.
    log.warn("NOT_FOUND: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), ex.getEntityName(), null, null,
                    null, null, null, null));
  }

  @ExceptionHandler(ApiKeyNotFoundException.class)
  public ResponseEntity<ErrorResponse> handleApiKeyNotFound(ApiKeyNotFoundException ex,
          WebRequest request) {
    // T800: 404 — caller-supplied key id did not resolve. WARN no stack.
    log.warn("API_KEY_NOT_FOUND: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(new ErrorResponse("NOT_FOUND", ex.getMessage(), "api-key:" + ex.getKeyId(), null,
                    null, null, null, null, null));
  }

  @ExceptionHandler(ScheduleConflictException.class)
  public ResponseEntity<ErrorResponse> handleScheduleConflict(ScheduleConflictException ex,
          WebRequest request) {
    // T800: 409 — duplicate schedule creation. WARN no stack.
    log.warn("CONFLICT: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.CONFLICT)
            .body(new ErrorResponse("CONFLICT", ex.getMessage(), null, null, null, null, null, null,
                    null));
  }

  @ExceptionHandler(BuilderUnavailableException.class)
  public ResponseEntity<ErrorResponse> handleBuilderUnavailable(BuilderUnavailableException ex,
          WebRequest request) {
    // T800: 503 — builder service down. Genuine incident, keep ERROR+stack.
    log.error("BUILDER_UNAVAILABLE: {}", ex.getMessage(), ex);
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(new ErrorResponse("BUILDER_UNAVAILABLE", ex.getMessage(), null, null, null, null,
                    null, null, null));
  }

  @ExceptionHandler(BuilderClientBusyException.class)
  public ResponseEntity<ErrorResponse> handleBuilderBusy(BuilderClientBusyException ex,
          WebRequest request) {
    // T800: 429 — rate-limited builder call. Expected under load, WARN no stack.
    log.warn("BUILDER_BUSY: {}", ex.getMessage());
    return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
            .body(new ErrorResponse("BUILDER_BUSY", ex.getMessage(), null, null, null, null, null,
                    null, null));
  }

  @ExceptionHandler(BuilderApiException.class)
  public ResponseEntity<ErrorResponse> handleBuilderApi(BuilderApiException ex,
          WebRequest request) {
    // T800: passthrough. 5xx = upstream incident (ERROR+stack), 4xx = builder rejected our call
    // (WARN no stack). Level chosen by status class so client-rejected calls don't page on-call.
    if (ex.getStatusCode().is5xxServerError()) {
      log.error("BUILDER_API_ERROR: {}", ex.getMessage(), ex);
    } else {
      log.warn("BUILDER_API_ERROR: {}", ex.getMessage());
    }
    return ResponseEntity.status(ex.getStatusCode())
            .body(new ErrorResponse(ex.getCode(), ex.getMessage(), null, null, null, null, null,
                    null, null));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException ex,
          WebRequest request) {
    // T800: 400 — caller-supplied bad input. WARN no stack.
    log.warn("IA_ERROR: {}", ex.getMessage());
    return dslExceptionMapper.handle(ex, request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleGeneral(Exception ex, WebRequest request) {
    log.error("ERROR: {}", ex.getMessage(), ex);
    return dslExceptionMapper.handle(ex, request);
  }
}
