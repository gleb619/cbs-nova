package cbs.nova.starter.controller;

import cbs.nova.dsl.ExplainReport;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.model.ValidationError;
import cbs.nova.starter.model.ValidationErrorsResponse;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.service.InputValidator;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import cbs.nova.starter.web.RequestIdFilter;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.util.List;

/**
 * Thin HTTP shell around {@link DslRuntimeService}. Each method only extracts path/body/header
 * inputs, delegates the work, and maps the resulting {@link RuntimeOutcome} to a
 * {@link ServerResponse}.
 */
@RequiredArgsConstructor
public class DslRuntimeHandler {

  private final DslRuntimeService service;
  private final DslPayloadSizeValidator payloadSizeValidator;
  private final InputValidator inputValidator;

  public ServerResponse preview(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    payloadSizeValidator.validateInput(request, dslRequest, name);
    return validationOrExecute(name, dslRequest,
            () -> respond(service.preview(name, dslRequest, requestId(request))));
  }

  public ServerResponse run(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    payloadSizeValidator.validateInput(request, dslRequest, name);
    return validationOrExecute(name, dslRequest,
            () -> respond(service.run(name, dslRequest, requestId(request))));
  }

  public ServerResponse explain(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    List<ValidationError> errors = inputValidator.validate(name, dslRequest.body());
    if (!errors.isEmpty()) {
      return validationResponse(errors);
    }
    return validationOrExecute(name, dslRequest,
            () -> respond(service.explain(name, dslRequest, requestId(request))));
  }

  private ServerResponse validationOrExecute(String name, DslRequest request,
          ThrowingResponse action) throws ServletException, IOException {
    List<ValidationError> errors = inputValidator.validate(name, request.body());
    if (!errors.isEmpty()) {
      return validationResponse(errors);
    }
    return action.get();
  }

  private ServerResponse respond(RuntimeOutcome outcome) {
    if (outcome.success()) {
      return ServerResponse.ok().body(outcome.value());
    }
    HttpStatus status = "PREVIEW_TIMEOUT".equals(outcome.error().code())
            ? HttpStatus.GATEWAY_TIMEOUT
            : HttpStatus.UNPROCESSABLE_ENTITY;
    return ServerResponse.status(status).body(outcome.error());
  }

  private ServerResponse validationResponse(List<ValidationError> errors) {
    return ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY)
            .body(new ValidationErrorsResponse(errors));
  }

  private String requestId(ServerRequest request) {
    String requestId = request.headers().firstHeader(RequestIdFilter.REQUEST_ID_HEADER);
    return requestId != null && !requestId.isBlank() ? requestId : null;
  }

  @FunctionalInterface
  private interface ThrowingResponse {

    ServerResponse get() throws ServletException, IOException;
  }
}
