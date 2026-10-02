package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.service.DslSignalService;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import tools.jackson.databind.exc.MismatchedInputException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

class DslSignalsHandlerTest {

  private static final String RUN_ID = "run-123";
  private static final String SIGNAL_NAME = "approve";
  private static final Object PAYLOAD = Map.of("amount", 100);

  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(
          new StringHttpMessageConverter(),
          new JacksonJsonHttpMessageConverter());

  private DslSignalService signalService;
  private ObjectMapper objectMapper;
  private DslSignalsHandler handler;

  @BeforeEach
  void setUp() {
    signalService = mock(DslSignalService.class);
    objectMapper = mock(ObjectMapper.class);
    handler = new DslSignalsHandler(signalService, objectMapper);
  }

  @Test
  void sendSignalNotFoundReturns404ErrorResponse() throws Exception {
    String json = body();
    when(objectMapper.readValue(eq(json), eq(DslSignalsHandler.SignalRequest.class)))
            .thenReturn(new DslSignalsHandler.SignalRequest(SIGNAL_NAME, PAYLOAD));
    when(signalService.sendSignal(RUN_ID, SIGNAL_NAME, PAYLOAD))
            .thenReturn(new DslSignalService.SignalResult(
                    DslSignalService.Outcome.NOT_FOUND, null, null));

    ServerResponse response = handler.sendSignal(postRequest(RUN_ID, json));

    assertThat(response.statusCode().value()).isEqualTo(404);
    ErrorResponse error = (ErrorResponse) ((EntityResponse<?>) response).entity();
    assertThat(error.code()).isEqualTo("NOT_FOUND");
    assertThat(error.message()).contains(RUN_ID);
    assertThat(error.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void sendSignalNotRunningReturns409ErrorResponseWithStatus() throws Exception {
    String currentStatus = "COMPLETED";
    String json = body();
    when(objectMapper.readValue(eq(json), eq(DslSignalsHandler.SignalRequest.class)))
            .thenReturn(new DslSignalsHandler.SignalRequest(SIGNAL_NAME, PAYLOAD));
    when(signalService.sendSignal(RUN_ID, SIGNAL_NAME, PAYLOAD))
            .thenReturn(new DslSignalService.SignalResult(
                    DslSignalService.Outcome.NOT_RUNNING, null, currentStatus));

    ServerResponse response = handler.sendSignal(postRequest(RUN_ID, json));

    assertThat(response.statusCode().value()).isEqualTo(409);
    ErrorResponse error = (ErrorResponse) ((EntityResponse<?>) response).entity();
    assertThat(error.code()).isEqualTo("CONFLICT");
    assertThat(error.message()).contains(RUN_ID).contains(currentStatus);
    assertThat(error.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void sendSignalUnknownSignalReturns400NamingSignalAndRunId() throws Exception {
    String json = body();
    when(objectMapper.readValue(eq(json), eq(DslSignalsHandler.SignalRequest.class)))
            .thenReturn(new DslSignalsHandler.SignalRequest(SIGNAL_NAME, PAYLOAD));
    when(signalService.sendSignal(RUN_ID, SIGNAL_NAME, PAYLOAD))
            .thenReturn(new DslSignalService.SignalResult(
                    DslSignalService.Outcome.UNKNOWN_SIGNAL, null, "RUNNING"));

    ServerResponse response = handler.sendSignal(postRequest(RUN_ID, json));

    assertThat(response.statusCode().value()).isEqualTo(400);
    ErrorResponse error = (ErrorResponse) ((EntityResponse<?>) response).entity();
    assertThat(error.code()).isEqualTo("UNKNOWN_SIGNAL");
    assertThat(error.message()).contains(SIGNAL_NAME).contains(RUN_ID);
    assertThat(error.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void sendSignalSentReturns200WithRunIdSignalNameAndStatus() throws Exception {
    String json = body();
    when(objectMapper.readValue(eq(json), eq(DslSignalsHandler.SignalRequest.class)))
            .thenReturn(new DslSignalsHandler.SignalRequest(SIGNAL_NAME, PAYLOAD));
    when(signalService.sendSignal(RUN_ID, SIGNAL_NAME, PAYLOAD))
            .thenReturn(new DslSignalService.SignalResult(
                    DslSignalService.Outcome.SENT, null, null));

    ServerResponse response = handler.sendSignal(postRequest(RUN_ID, json));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) ((EntityResponse<?>) response).entity();
    assertThat(entity).containsEntry("runId", RUN_ID)
            .containsEntry("signalName", SIGNAL_NAME)
            .containsEntry("status", "sent");
  }

  @Test
  void parseBodyThrowsIllegalArgumentExceptionWhenObjectMapperFails() throws Exception {
    when(objectMapper.readValue(any(String.class), eq(DslSignalsHandler.SignalRequest.class)))
            .thenThrow(MismatchedInputException.from(null, DslSignalsHandler.SignalRequest.class,
                    "parse failure"));

    assertThatThrownBy(() -> handler.sendSignal(postRequest(RUN_ID, "not-json")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid signal request body")
            .hasCauseInstanceOf(MismatchedInputException.class);
  }

  @Test
  void querySignalStateNullReturns404ErrorResponse() {
    when(signalService.querySignalState(RUN_ID)).thenReturn(null);

    ServerResponse response = handler.querySignalState(getRequest(RUN_ID));

    assertThat(response.statusCode().value()).isEqualTo(404);
    ErrorResponse error = (ErrorResponse) ((EntityResponse<?>) response).entity();
    assertThat(error.code()).isEqualTo("NOT_FOUND");
    assertThat(error.message()).contains(RUN_ID);
    assertThat(error.runId()).isEqualTo(RUN_ID);
  }

  @Test
  void querySignalStateNonNullReturns200WithRunIdAndState() {
    Map<String, Object> state = Map.of("received", true);
    when(signalService.querySignalState(RUN_ID)).thenReturn(state);

    ServerResponse response = handler.querySignalState(getRequest(RUN_ID));

    assertThat(response.statusCode().value()).isEqualTo(200);
    @SuppressWarnings("unchecked")
    Map<String, Object> entity = (Map<String, Object>) ((EntityResponse<?>) response).entity();
    assertThat(entity).containsEntry("runId", RUN_ID)
            .containsEntry("signalState", state);
  }

  @Test
  void sendSignalRespectsGlobalRequestBodyLimit() {
    // The router's RequestBodyLimitFilter rejects oversized bodies with
    // DslPayloadTooLargeException before the handler runs; DslExceptionHandler then maps that
    // to a 413 with the PAYLOAD_TOO_LARGE ErrorResponse. Verify the filter short-circuit here;
    // the response shape is pinned by DslExceptionHandlerTest.
    RequestBodyLimitFilter filter = new RequestBodyLimitFilter(
            new cbs.nova.starter.config.properties.CbsNovaWebProperties(8L));
    String hugeJson = "{\"signalName\":\"" + SIGNAL_NAME + "\",\"payload\":"
            + "{\"data\":\"" + "x".repeat(2048) + "\"}}";
    ServerRequest oversized = postRequest(RUN_ID, hugeJson);

    assertThatThrownBy(() -> filter.filter(oversized, handler::sendSignal))
            .isInstanceOfSatisfying(DslPayloadTooLargeException.class, ex ->
                    assertThat(ex.getEntityName()).isEqualTo(RequestBodyLimitFilter.ENTITY_NAME));
  }

  private static String body() {
    return "{\"signalName\":\"" + SIGNAL_NAME + "\",\"payload\":{\"amount\":100}}";
  }

  private static ServerRequest getRequest(String runId) {
    MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/dsl/signals/" + runId);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("runId", runId));
    return ServerRequest.create(req, CONVERTERS);
  }

  private static ServerRequest postRequest(String runId, String body) {
    MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/dsl/signals/" + runId);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of("runId", runId));
    req.setContentType("application/json");
    req.setContent(body.getBytes(StandardCharsets.UTF_8));
    return ServerRequest.create(req, CONVERTERS);
  }
}
