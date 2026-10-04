package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.exception.ApiKeyNotFoundException;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.converter.DslExceptionMapper;
import cbs.nova.dsl.model.ErrorResponse;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.sentry.Sentry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

class DslExceptionHandlerTest {

  private static final Logger HANDLER_LOGGER = (Logger) LoggerFactory
          .getLogger(DslExceptionHandler.class);
  private ListAppender<ILoggingEvent> listAppender;

  @BeforeEach
  void attachListAppender() {
    listAppender = new ListAppender<>();
    listAppender.setContext(HANDLER_LOGGER.getLoggerContext());
    listAppender.start();
    HANDLER_LOGGER.addAppender(listAppender);
  }

  @AfterEach
  void detachListAppender() {
    if (listAppender != null) {
      HANDLER_LOGGER.detachAppender(listAppender);
      listAppender.stop();
      listAppender.list.clear();
    }
  }

  @Test
  void generalExceptionMapsTo500WithInternalErrorCode() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/general"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("boom"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void generalExceptionTagsRunIdFromRequestAttributeForSentry() throws Exception {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      MockMvc mvc = MockMvcBuilders
              .standaloneSetup(new ThrowingController())
              .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
              .setMessageConverters(new JacksonJsonHttpMessageConverter())
              .build();

      mvc.perform(get("/throw/general").requestAttr("runId", "run-xyz"))
              .andExpect(status().isInternalServerError());

      sentry.verify(() -> Sentry.setTag("runId", "run-xyz"));
      sentry.verify(() -> Sentry.captureException(Mockito.any(RuntimeException.class)));
    }
  }

  @Test
  void illegalArgumentExceptionMapsTo400WithBadRequestCode() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/bad"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("bad arg"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void dslExceptionMapsTo422WithStructuredFields() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/dsl"))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.code").value("EXECUTION_FAILED"))
            .andExpect(jsonPath("$.message").value("dsl failed"))
            .andExpect(jsonPath("$.runId").value("run-abc"))
            .andExpect(jsonPath("$.exceptionId").isString());
  }

  @Test
  void dslExceptionTagsRunIdFromExceptionForSentry() throws Exception {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      MockMvc mvc = MockMvcBuilders
              .standaloneSetup(new ThrowingController())
              .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
              .setMessageConverters(new JacksonJsonHttpMessageConverter())
              .build();

      mvc.perform(get("/throw/dsl"))
              .andExpect(status().isUnprocessableEntity());

      sentry.verify(() -> Sentry.setTag("runId", "run-abc"));
      sentry.verify(() -> Sentry.captureException(Mockito.any(DslException.class)));
    }
  }

  @Test
  void customMapperOverridesResponseStatusAndBody() throws Exception {
    DslExceptionMapper customMapper = new DslExceptionMapper() {
      @Override
      public ResponseEntity<ErrorResponse> handle(Exception exception, WebRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse("CUSTOM_CODE", "custom msg", "customEntity", "custom-run",
                        null, "custom-ex", null, null, null));
      }
    };

    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(customMapper))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/general"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CUSTOM_CODE"))
            .andExpect(jsonPath("$.message").value("custom msg"))
            .andExpect(jsonPath("$.entityName").value("customEntity"))
            .andExpect(jsonPath("$.runId").value("custom-run"))
            .andExpect(jsonPath("$.exceptionId").value("custom-ex"));
  }

  @Test
  void payloadTooLargeExceptionMapsTo413WithPayloadTooLargeCode() throws Exception {
    MockMvc mvc = MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();

    mvc
            .perform(get("/throw/payload-too-large"))
            .andExpect(status().isPayloadTooLarge())
            .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
            .andExpect(jsonPath("$.message").value(
                    "Request body for 'BigProcess' exceeds maximum input size (limit 1024 bytes, actual 2048 bytes)"))
            .andExpect(jsonPath("$.entityName").value("BigProcess"))
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void definitionNotFoundExceptionMapsTo404WithEntityName() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/definition-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("No published definition: MissingProcess"))
            .andExpect(jsonPath("$.entityName").value("MissingProcess"))
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void apiKeyNotFoundExceptionMapsTo404WithPrefixedEntityName() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/api-key-not-found"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("API key not found: id=42"))
            .andExpect(jsonPath("$.entityName").value("api-key:42"))
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void scheduleConflictExceptionMapsTo409WithConflictCode() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/schedule-conflict"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.message").value("Schedule already exists: nightly-sync"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void builderUnavailableExceptionMapsTo503WithBuilderUnavailableCode() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/builder-unavailable"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("BUILDER_UNAVAILABLE"))
            .andExpect(jsonPath("$.message").value("builder offline"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void builderClientBusyExceptionMapsTo429WithBuilderBusyCode() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/builder-busy"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.code").value("BUILDER_BUSY"))
            .andExpect(jsonPath("$.message").value("builder at capacity"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void builderApiExceptionPassesThroughClientErrorStatusAndCode() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/builder-api-400"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BUILDER_BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("builder rejected input"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  @Test
  void builderApiExceptionPassesThroughUpstreamErrorStatusAndCode() throws Exception {
    MockMvc mvc = buildMvc();

    mvc
            .perform(get("/throw/builder-api-502"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.code").value("BUILDER_UPSTREAM_FAILURE"))
            .andExpect(jsonPath("$.message").value("builder upstream exploded"))
            .andExpect(jsonPath("$.entityName").doesNotExist())
            .andExpect(jsonPath("$.runId").doesNotExist())
            .andExpect(jsonPath("$.exceptionId").doesNotExist());
  }

  private static MockMvc buildMvc() {
    return MockMvcBuilders
            .standaloneSetup(new ThrowingController())
            .setControllerAdvice(new DslExceptionHandler(new DefaultDslExceptionMapper()))
            .setMessageConverters(new JacksonJsonHttpMessageConverter())
            .build();
  }

  // --- T800 log-level assertions -------------------------------------------------
  // These complement the status/body tests above. Each handler must emit a log line at the
  // severity appropriate to the failure class: client-caused 4xx outcomes at WARN (message only,
  // no stack), server-side 5xx and unhandled exceptions at ERROR (with stack). The thresholds
  // matter because `sentry.logging.minimum-event-level` promotes ERROR+ lines to Bugsink events
  // — a 404 logged at ERROR would page on-call for a typo'd definition name.

  @Test
  void definitionNotFoundLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/definition-not-found"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void apiKeyNotFoundLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/api-key-not-found"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void scheduleConflictLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/schedule-conflict"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void builderBusyLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/builder-busy"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void badRequestLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/bad"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void builderApiClientErrorLogsAtWarnWithoutStackTrace() throws Exception {
    buildMvc().perform(get("/throw/builder-api-400"));

    assertThat(lastEventLevel()).isEqualTo(Level.WARN);
    assertThat(lastEventHasStackTrace()).isFalse();
  }

  @Test
  void builderApiServerErrorLogsAtErrorWithStackTrace() throws Exception {
    buildMvc().perform(get("/throw/builder-api-502"));

    assertThat(lastEventLevel()).isEqualTo(Level.ERROR);
    assertThat(lastEventHasStackTrace()).isTrue();
  }

  @Test
  void builderUnavailableLogsAtErrorWithStackTrace() throws Exception {
    buildMvc().perform(get("/throw/builder-unavailable"));

    assertThat(lastEventLevel()).isEqualTo(Level.ERROR);
    assertThat(lastEventHasStackTrace()).isTrue();
  }

  @Test
  void generalExceptionLogsAtErrorWithStackTrace() throws Exception {
    buildMvc().perform(get("/throw/general"));

    assertThat(lastEventLevel()).isEqualTo(Level.ERROR);
    assertThat(lastEventHasStackTrace()).isTrue();
  }

  private Level lastEventLevel() {
    assertThat(listAppender.list).as("expected at least one log event from DslExceptionHandler")
            .isNotEmpty();
    return listAppender.list.get(listAppender.list.size() - 1).getLevel();
  }

  private boolean lastEventHasStackTrace() {
    return listAppender.list.get(listAppender.list.size() - 1).getThrowableProxy() != null;
  }

  @RestController
  static class ThrowingController {

    @GetMapping("/throw/general")
    String throwGeneral() {
      throw new RuntimeException("boom");
    }

    @GetMapping("/throw/bad")
    String throwBad() {
      throw new IllegalArgumentException("bad arg");
    }

    @GetMapping("/throw/dsl")
    String throwDsl() {
      throw new DslException("run-abc", DslErrorCode.EXECUTION_FAILED, "dsl failed");
    }

    @GetMapping("/throw/payload-too-large")
    String throwPayloadTooLarge() {
      throw new DslPayloadTooLargeException(1024L, 2048L, "BigProcess");
    }

    @GetMapping("/throw/definition-not-found")
    String throwDefinitionNotFound() {
      throw new DefinitionNotFoundException("MissingProcess");
    }

    @GetMapping("/throw/api-key-not-found")
    String throwApiKeyNotFound() {
      throw new ApiKeyNotFoundException(42L);
    }

    @GetMapping("/throw/schedule-conflict")
    String throwScheduleConflict() {
      throw new ScheduleConflictException("nightly-sync");
    }

    @GetMapping("/throw/builder-unavailable")
    String throwBuilderUnavailable() {
      throw new BuilderUnavailableException("builder offline");
    }

    @GetMapping("/throw/builder-busy")
    String throwBuilderBusy() {
      throw new BuilderClientBusyException("builder at capacity");
    }

    @GetMapping("/throw/builder-api-400")
    String throwBuilderApiBadRequest() {
      throw new BuilderApiException(HttpStatus.BAD_REQUEST, "BUILDER_BAD_REQUEST",
              "builder rejected input");
    }

    @GetMapping("/throw/builder-api-502")
    String throwBuilderApiBadGateway() {
      throw new BuilderApiException(HttpStatus.BAD_GATEWAY, "BUILDER_UPSTREAM_FAILURE",
              "builder upstream exploded");
    }
  }
}
