package cbs.nova.starter.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.router.DslRuntimeRouterConfiguration;
import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.DslRequest;
import cbs.nova.starter.model.RuntimeOutcome;
import cbs.nova.starter.model.ValidationError;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.service.IdempotencyKeys;
import cbs.nova.starter.service.InputValidator;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Unit characterization spec for {@link DslRuntimeHandler}: the HTTP shell around a mocked
 * {@link DslRuntimeService}, pinning input validation, correlation/idempotency handling for
 * {@code run}, and the outcome-to-response mapping for {@code preview}, {@code run},
 * {@code explain}, and {@code hierarchy}.
 */
class DslRuntimeHandlerTest {

  private DslRuntimeService service;
  private DslPayloadSizeValidator payloadSizeValidator;
  private InputValidator inputValidator;
  private static RequestBodyLimitFilter requestBodyLimitFilter;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    requestBodyLimitFilter = new RequestBodyLimitFilter(new CbsNovaWebProperties(0L));
    service = mock(DslRuntimeService.class);
    payloadSizeValidator = mock(DslPayloadSizeValidator.class);
    inputValidator = mock(InputValidator.class);

    DslRuntimeHandler handler = new DslRuntimeHandler(service, payloadSizeValidator,
            inputValidator);
    DslRuntimeRouterConfiguration router = new DslRuntimeRouterConfiguration();

    mockMvc = MockMvcBuilders
            .routerFunctions(router.dslRuntimeRouter(handler, requestBodyLimitFilter))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .build();
  }

  @Test
  void previewValidatesInputAndReturns200WhenServiceSucceeds() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.preview(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of("preview", true)));

    mockMvc.perform(post("/api/dsl/preview/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{\"foo\":\"bar\"}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.preview").value(true));

    verify(payloadSizeValidator).validateInput(any(), any(DslRequest.class), eq("LoanA"));
    verify(service).preview(eq("LoanA"), any(DslRequest.class), isNull());
  }

  @Test
  void previewReturns422WhenInputValidationFails() throws Exception {
    ValidationError error = new ValidationError("body", "missing field", "ERROR");
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of(error));

    mockMvc.perform(post("/api/dsl/preview/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("body"))
            .andExpect(jsonPath("$.errors[0].message").value("missing field"));

    verifyNoServiceInteraction();
  }

  @Test
  void previewPassesRequestIdThroughToService() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.preview(eq("LoanA"), any(DslRequest.class), eq("req-123")))
            .thenReturn(RuntimeOutcome.ok(Map.of()));

    mockMvc.perform(post("/api/dsl/preview/LoanA")
            .header(StarterConstants.REQUEST_ID_HEADER, "req-123")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk());

    verify(service).preview(eq("LoanA"), any(DslRequest.class), eq("req-123"));
  }

  @Test
  void previewReturns504OnPreviewTimeoutOutcome() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    ErrorResponse error = new ErrorResponse("PREVIEW_TIMEOUT", "timed out", "LoanA", null, null,
            null, null, null, null);
    when(service.preview(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.error(error));

    mockMvc.perform(post("/api/dsl/preview/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value("PREVIEW_TIMEOUT"));
  }

  @Test
  void previewReturns422OnNonTimeoutFailureOutcome() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    ErrorResponse error = new ErrorResponse("DSL_EXECUTION", "boom", "LoanA", null, null, null,
            null, null, null);
    when(service.preview(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.error(error));

    mockMvc.perform(post("/api/dsl/preview/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("DSL_EXECUTION"));
  }

  @Test
  void runRequiresValidCorrelationId() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());

    mockMvc.perform(post("/api/dsl/run/LoanA")
            .header(StarterConstants.CORRELATION_ID_HEADER, "bad value!!!")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_CORRELATION_ID"));

    verifyNoServiceInteraction();
  }

  @Test
  void runRejectsInvalidIdempotencyKey() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());

    mockMvc.perform(post("/api/dsl/run/LoanA")
            .header(StarterConstants.IDEMPOTENCY_KEY_HEADER, "bad key!!!")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"));

    verifyNoServiceInteraction();
  }

  @Test
  void runDerivesRunIdFromValidIdempotencyKey() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    String key = "my-key";
    String runId = IdempotencyKeys.deriveRunId("LoanA", key);
    when(service.run(eq("LoanA"), any(DslRequest.class), isNull(), eq(runId), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of("ran", true)));

    mockMvc.perform(post("/api/dsl/run/LoanA")
            .header(StarterConstants.IDEMPOTENCY_KEY_HEADER, key)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ran").value(true));

    verify(service).run(eq("LoanA"), any(DslRequest.class), isNull(), eq(runId), isNull());
  }

  @Test
  void runPassesNullRunIdWhenIdempotencyKeyAbsent() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.run(eq("LoanA"), any(DslRequest.class), isNull(), isNull(), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of()));

    mockMvc.perform(post("/api/dsl/run/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk());

    verify(service).run(eq("LoanA"), any(DslRequest.class), isNull(), isNull(), isNull());
  }

  @Test
  void runAddsReplayedHeaderForReplayedOutcome() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.run(eq("LoanA"), any(DslRequest.class), isNull(), isNull(), isNull()))
            .thenReturn(RuntimeOutcome.okReplayed(Map.of("cached", true)));

    mockMvc.perform(post("/api/dsl/run/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotency-Replayed", "true"))
            .andExpect(jsonPath("$.cached").value(true));
  }

  @Test
  void explainReturns422OnValidationErrors() throws Exception {
    ValidationError error = new ValidationError("body", "invalid", "ERROR");
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of(error));

    mockMvc.perform(post("/api/dsl/explain/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].severity").value("ERROR"));

    verifyNoServiceInteraction();
  }

  @Test
  void explainDelegatesToServiceWhenValid() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.explain(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of("explain", "report")));

    mockMvc.perform(post("/api/dsl/explain/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.explain").value("report"));
  }

  @Test
  void hierarchyReturns422OnValidationErrors() throws Exception {
    ValidationError error = new ValidationError("body", "invalid", "ERROR");
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of(error));

    mockMvc.perform(post("/api/dsl/hierarchy/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("body"));

    verifyNoServiceInteraction();
  }

  @Test
  void hierarchyDelegatesToServiceWhenValid() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.hierarchy(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of("hierarchy", "graph")));

    mockMvc.perform(post("/api/dsl/hierarchy/LoanA")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hierarchy").value("graph"));
  }

  @Test
  void blankRequestIdIsTreatedAsAbsent() throws Exception {
    when(inputValidator.validate(eq("LoanA"), any())).thenReturn(List.of());
    when(service.explain(eq("LoanA"), any(DslRequest.class), isNull()))
            .thenReturn(RuntimeOutcome.ok(Map.of()));

    mockMvc.perform(post("/api/dsl/explain/LoanA")
            .header(StarterConstants.REQUEST_ID_HEADER, "   ")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"body\":{}}"))
            .andExpect(status().isOk());

    verify(service).explain(eq("LoanA"), any(DslRequest.class), isNull());
  }

  private void verifyNoServiceInteraction() {
    verify(service, org.mockito.Mockito.never()).preview(any(), any(), any());
    verify(service, org.mockito.Mockito.never()).run(any(), any(), any(), any(), any());
    verify(service, org.mockito.Mockito.never()).explain(any(), any(), any());
    verify(service, org.mockito.Mockito.never()).hierarchy(any(), any(), any());
  }
}
