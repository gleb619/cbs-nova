package cbs.nova.starter.controller;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.service.DslSignalService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DslSignalsHandler {

  private final DslSignalService signalService;
  private final ObjectMapper objectMapper;

  public ServerResponse sendSignal(ServerRequest request) {
    String runId = request.pathVariable("runId");
    SignalRequest body = parseBody(request);
    String signalName = body.signalName();
    Object payload = body.payload();

    DslSignalService.SignalResult result = signalService.sendSignal(runId, signalName, payload);

    return switch (result.outcome()) {
      case NOT_FOUND -> ServerResponse.status(HttpStatus.NOT_FOUND)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + runId, null,
                      runId, null, null, null, null, null));
      case NOT_RUNNING -> ServerResponse.status(HttpStatus.CONFLICT)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ErrorResponse("CONFLICT",
                      "Execution run is not running: " + runId + " (status "
                              + result.currentStatus() + ")",
                      null, runId, null, null, null, null, null));
      case SENT -> ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(Map.of("runId", runId, "signalName", signalName, "status", "sent"));
    };
  }

  public ServerResponse querySignalState(ServerRequest request) {
    String runId = request.pathVariable("runId");
    Map<String, Object> state = signalService.querySignalState(runId);
    if (state == null) {
      return ServerResponse.status(HttpStatus.NOT_FOUND)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ErrorResponse("NOT_FOUND", "Execution run not found: " + runId, null,
                      runId, null, null, null, null, null));
    }
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("runId", runId, "signalState", state));
  }

  private @NonNull SignalRequest parseBody(ServerRequest request) {
    try {
      return objectMapper.readValue(request.body(String.class), SignalRequest.class);
    } catch (Exception e) {
      throw new IllegalArgumentException("Invalid signal request body: " + e.getMessage(), e);
    }
  }

  public record SignalRequest(@NonNull String signalName, @Nullable Object payload) {
  }
}
