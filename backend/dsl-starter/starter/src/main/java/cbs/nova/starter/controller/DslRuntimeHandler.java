package cbs.nova.starter.controller;

import cbs.nova.dsl.ExplainReport;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import cbs.nova.starter.web.RequestIdFilter;
import jakarta.servlet.ServletException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;

/**
 * Thin HTTP shell around {@link DslRuntimeService}. Each method only extracts path/body/header
 * inputs, delegates the work, and maps the resulting {@link RuntimeOutcome} to a
 * {@link ServerResponse}.
 */
@RequiredArgsConstructor
public class DslRuntimeHandler {

  private final DslRuntimeService service;
  private final DslPayloadSizeValidator payloadSizeValidator;

  public ServerResponse preview(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    payloadSizeValidator.validateInput(request, dslRequest, name);
    return respond(service.preview(name, dslRequest, requestId(request)));
  }

  public ServerResponse run(ServerRequest request) throws ServletException, IOException {
    String name = request.pathVariable("name");
    DslRequest dslRequest = request.body(DslRequest.class);
    payloadSizeValidator.validateInput(request, dslRequest, name);
    return respond(service.run(name, dslRequest, requestId(request)));
  }

  public ServerResponse explain(ServerRequest request) throws ServletException, IOException {
    ExplainReport report = service.explain(
            request.pathVariable("name"),
            request.body(DslRequest.class),
            requestId(request));
    return ServerResponse.ok().body(report);
  }

  private ServerResponse respond(RuntimeOutcome outcome) {
    return outcome.success()
            ? ServerResponse.ok().body(outcome.value())
            : ServerResponse.status(HttpStatus.UNPROCESSABLE_ENTITY).body(outcome.error());
  }

  private String requestId(ServerRequest request) {
    String requestId = request.headers().firstHeader(RequestIdFilter.REQUEST_ID_HEADER);
    return requestId != null && !requestId.isBlank() ? requestId : null;
  }
}
