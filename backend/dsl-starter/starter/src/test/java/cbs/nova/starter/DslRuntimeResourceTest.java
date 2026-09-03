package cbs.nova.starter;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.DslRuntime;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.starter.core.pipe.PreviewTimeoutException;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
import cbs.nova.starter.config.router.DslRuntimeRouterConfiguration;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.config.properties.InputValidationProperties;
import cbs.nova.starter.controller.DslRuntimeHandler;
import cbs.nova.starter.converter.DslRuntimeMapper;
import cbs.nova.starter.logging.LoggingExecutionListener;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.service.IdempotencyKeys;
import cbs.nova.starter.service.IdempotentReplayException;
import cbs.nova.starter.service.InputValidator;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mapstruct.factory.Mappers;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class DslRuntimeResourceTest {

  private final DslRuntime dslRuntime = mock(DslRuntime.class);
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();

    var loggingProperties = new CbsNovaLoggingProperties(
            CbsNovaLoggingProperties.Level.INFO,
            CbsNovaLoggingProperties.Level.INFO,
            false);
    DslRuntimeMapper mapper = Mappers.getMapper(DslRuntimeMapper.class);
    DslRuntimeService service = new DslRuntimeService(
            dslRuntime,
            new ContextFactory(),
            new LoggingExecutionListener(loggingProperties),
            mapper);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(
            new ObjectMapper(), new DslRunsProperties());
    InputValidator inputValidator = new InputValidator(
            new JacksonJsonSchemaGenerator(),
            new InputValidationProperties(true),
            Caffeine.newBuilder().build());
    DslRuntimeHandler handler = new DslRuntimeHandler(service, validator, inputValidator);
    DslRuntimeRouterConfiguration router = new DslRuntimeRouterConfiguration();
    mockMvc = MockMvcBuilders.routerFunctions(router.dslRuntimeRouter(handler)).build();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void previewReturns200OnSuccess() throws Exception {
    PreviewReport report = new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            true,
            "pong",
            List.of("started: Ping", "mode: PREVIEW", "completed successfully"),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of());
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Ping")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"hello\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ping"))
            .andExpect(jsonPath("$.mode").value("PREVIEW"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.output").value("pong"))
            .andExpect(jsonPath("$.executionTrace[0]").value("started: Ping"))
            .andExpect(jsonPath("$.callCounts").isMap());
  }

  @Test
  void previewReturns422OnFailure() throws Exception {
    PreviewReport report = new PreviewReport(
            "Fail",
            ExecutionMode.PREVIEW,
            false,
            null,
            List.of(),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of(new PreviewErrorDetail(PreviewErrorCode.UNKNOWN_ERROR,
                    "boom", "Review the failure", Map.of())));
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Fail"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Fail")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"x\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("UNKNOWN_ERROR"))
            .andExpect(jsonPath("$.message").value("boom"))
            .andExpect(jsonPath("$.entityName").value("Fail"))
            .andExpect(jsonPath("$.runId").exists())
            .andExpect(jsonPath("$.exceptionId").exists());
  }

  @Test
  void runReturns200OnSuccess() throws Exception {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isOk());
  }

  @Test
  void runReturns422OnGenericFailure() throws Exception {
    doReturn(Result.failure(new RuntimeException("exec error")))
            .when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
            .andExpect(jsonPath("$.message").value("exec error"))
            .andExpect(jsonPath("$.runId").exists());
  }

  @Test
  void runReturns422WithDslExceptionFields() throws Exception {
    var ex = new DslException("run-abc", DslErrorCode.ENTITY_NOT_FOUND, "not found");
    doReturn(Result.failure(ex)).when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("ENTITY_NOT_FOUND"))
            .andExpect(jsonPath("$.runId").value("run-abc"))
            .andExpect(jsonPath("$.exceptionId").isString());
  }

  @Test
  void explainReturns200WithReport() throws Exception {
    ExplainReport report = new ExplainReport(
            "P", "desc",
            List.of(), List.of(), Map.of(), null, null, null, List.of(), null, List.of(), null);
    doReturn(report).when(dslRuntime).explain(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/explain/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"in\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("P"))
            .andExpect(jsonPath("$.description").value("desc"));
  }

  @Test
  void previewReturns504ForPreviewTimeout() throws Exception {
    doReturn(Result.failure(new PreviewTimeoutException("Slow", Duration.ofMillis(100))))
            .when(dslRuntime).preview(eq("Slow"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Slow")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"in\"}"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value("PREVIEW_TIMEOUT"))
            .andExpect(jsonPath("$.entityName").value("Slow"))
            .andExpect(jsonPath("$.runId").exists())
            .andExpect(jsonPath("$.exceptionId").exists());
  }

  @Test
  void explainReturns504ForPreviewTimeout() throws Exception {
    ExplainReport report = new ExplainReport(
            "Slow",
            "desc",
            List.of(),
            List.of(),
            Map.of(),
            null,
            null,
            null,
            List.of(),
            null,
            List.of(new PreviewErrorDetail(PreviewErrorCode.PREVIEW_TIMEOUT,
                    "timed out", "increase timeout", Map.of())),
            null);
    doReturn(report).when(dslRuntime).explain(eq("Slow"), any());

    mockMvc
            .perform(
                    post("/api/dsl/explain/Slow")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"in\"}"))
            .andExpect(status().isGatewayTimeout())
            .andExpect(jsonPath("$.code").value("PREVIEW_TIMEOUT"))
            .andExpect(jsonPath("$.entityName").value("Slow"))
            .andExpect(jsonPath("$.runId").exists())
            .andExpect(jsonPath("$.exceptionId").exists());
  }

  @Test
  void previewAcceptsRequestWithoutMocksField() throws Exception {
    PreviewReport report = new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            true,
            "pong",
            List.of("started: Ping"),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of());
    doReturn(Result.success(report)).when(dslRuntime).preview(eq("Ping"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/Ping")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": \"hello\", \"metadata\": {}}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("Ping"));
  }

  @Test
  void invalidRegisteredProcessBodyReturns422WithFieldPointer() throws Exception {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ValidatedProcess")
                    .input(SampleInput.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    doReturn(Result.success(new PreviewReport(
            "ValidatedProcess", ExecutionMode.PREVIEW, true, "ok",
            List.of(), List.of(), Map.of(), null, List.of(), null, List.of())))
            .when(dslRuntime).preview(eq("ValidatedProcess"), any());

    mockMvc
            .perform(
                    post("/api/dsl/preview/ValidatedProcess")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": {\"name\": 123}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("$.name"))
            .andExpect(jsonPath("$.errors[0].message").value("expected type string"))
            .andExpect(jsonPath("$.errors[0].severity").value("error"));
  }

  @Test
  void runRejectsInvalidBodyWith422AndFieldPointer() throws Exception {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ValidatedRun")
                    .input(SampleInput.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    mockMvc
            .perform(
                    post("/api/dsl/run/ValidatedRun")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": {\"name\": 123}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("$.name"))
            .andExpect(jsonPath("$.errors[0].message").value("expected type string"));
  }

  @Test
  void explainRejectsInvalidBodyWith422AndFieldPointer() throws Exception {
    GlobalManager.globalManager().registerProcess(
            Dsl.process("ValidatedExplain")
                    .input(SampleInput.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    mockMvc
            .perform(
                    post("/api/dsl/explain/ValidatedExplain")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"body\": {\"name\": 123}}"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.errors[0].field").value("$.name"))
            .andExpect(jsonPath("$.errors[0].message").value("expected type string"));
  }

  public record SampleInput(String name) {
  }

  @Test
  void runRejectsInvalidIdempotencyKeyWith400() throws Exception {
    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "bad key!")
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_IDEMPOTENCY_KEY"))
            .andExpect(jsonPath("$.message").value("Invalid Idempotency-Key header"));
  }

  @Test
  void runAcceptsValidIdempotencyKeyAndUsesDerivedRunId() throws Exception {
    doReturn(Result.success("output")).when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "valid-key_1.0")
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").value("output"));

    ArgumentCaptor<Context<?>> captor = ArgumentCaptor
            .forClass(Context.class);
    verify(dslRuntime).run(eq("P"), captor.capture());
    assertThat(captor.getValue().runId())
            .isEqualTo(IdempotencyKeys.deriveRunId("P", "valid-key_1.0"));
  }

  @Test
  void runReturnsReplayedResponseForIdempotentReplay() throws Exception {
    doReturn(Result.failure(new IdempotentReplayException("idem-abc")))
            .when(dslRuntime).run(eq("P"), any());

    mockMvc
            .perform(
                    post("/api/dsl/run/P")
                            .contentType(MediaType.APPLICATION_JSON)
                            .header("Idempotency-Key", "key-1")
                            .content("{\"body\": \"input\"}"))
            .andExpect(status().isOk())
            .andExpect(header().string("Idempotency-Replayed", "true"))
            .andExpect(jsonPath("$.runId").value("idem-abc"))
            .andExpect(jsonPath("$.status").value("REPLAYED"));
  }
}
