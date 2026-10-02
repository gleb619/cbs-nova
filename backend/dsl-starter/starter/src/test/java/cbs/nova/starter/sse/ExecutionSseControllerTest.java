package cbs.nova.starter.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.dsl.history.DslRun;
import cbs.nova.dsl.repository.InMemoryDslRunRepository;
import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.converter.DslExceptionMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

class ExecutionSseControllerTest {

  private static final DslExceptionMapper NO_OP_MAPPER = (ex, req) -> null;

  private final InMemoryDslRunRepository repository = new InMemoryDslRunRepository(
          InMemoryDslRunRepository.NO_OP_EVICTION);
  private final ExecutionSseService sseService = new ExecutionSseService(new CbsSseProperties(1));
  private final ExecutionSseService sseServiceHighCap = new ExecutionSseService(
          new CbsSseProperties(500));
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    // Default cap=1 → second subscribe on a different id triggers 429.
    ExecutionSseController controller = new ExecutionSseController(sseService, repository);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setHandlerExceptionResolvers(buildExceptionResolver(NO_OP_MAPPER))
            .build();
  }

  @Test
  void unknownRunReturns404() throws Exception {
    mockMvc.perform(get("/api/executions/missing/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isNotFound());
  }

  @Test
  void knownRunOpensSseStreamAndSendsConnectedComment() throws Exception {
    repository.save(DslRun.builder()
            .runId("run-live")
            .processName("Loan")
            .status("RUNNING")
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());

    mockMvc.perform(get("/api/executions/run-live/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(content().string(containsString(":connected")));

    assertThat(sseService.subscriberCount("run-live")).isEqualTo(1);
  }

  @Test
  void overCapReturns429WithErrorResponseShapeAndCreatesNoEmitter() throws Exception {
    // cap=1 → second subscribe on a distinct id must yield 429.
    repository.save(DslRun.builder()
            .runId("run-a")
            .processName("Loan")
            .status("RUNNING")
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());
    repository.save(DslRun.builder()
            .runId("run-b")
            .processName("Loan")
            .status("RUNNING")
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());

    // First subscriber occupies the only slot.
    mockMvc.perform(get("/api/executions/run-a/events")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk());

    // Second subscribe on a DIFFERENT id → 429.
    // Accept includes application/json so the ErrorResponse body can be produced; the success
    // path's text/event-stream Accept alone would yield HttpMediaTypeNotAcceptableException.
    mockMvc.perform(get("/api/executions/run-b/events")
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("SUBSCRIBER_CAP_EXCEEDED"))
            .andExpect(jsonPath("$.message", containsString("cap=1")));

    // No emitter was created for the rejected id.
    assertThat(sseService.subscriberCount("run-b")).isEqualTo(0);
  }

  @Test
  void reSubscribeSameIdIsNotCountedAgainstCap() throws Exception {
    // High-cap service to ensure we exercise the replace-not-counted path independently of the
    // cap rejection path.
    ExecutionSseController controller = new ExecutionSseController(sseServiceHighCap, repository);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setHandlerExceptionResolvers(buildExceptionResolver(NO_OP_MAPPER))
            .build();

    repository.save(DslRun.builder()
            .runId("run-x")
            .processName("Loan")
            .status("RUNNING")
            .startedAt(Instant.now())
            .executionMode("RUN")
            .build());

    mvc.perform(get("/api/executions/run-x/events")
            .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isOk());
    mvc.perform(get("/api/executions/run-x/events")
            .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isOk());

    assertThat(sseServiceHighCap.activeSubscriberCount()).isEqualTo(1);
  }

  /**
   * Mirrors {@code DslPromoteHandlerTest} — standalone-setup needs an explicit
   * {@link ExceptionHandlerExceptionResolver} wired to a Spring context that has the
   * {@link DslExceptionHandler} bean; the {@code setControllerAdvice} shortcut does NOT pick up
   * {@code @ExceptionHandler} methods on the advice in standalone mode.
   */
  private static ExceptionHandlerExceptionResolver buildExceptionResolver(
          DslExceptionMapper mapper) {
    AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
    ctx.registerBean(DslExceptionHandler.class, () -> new DslExceptionHandler(mapper));
    ctx.registerBean(DefaultDslExceptionMapper.class);
    ctx.refresh();
    ExceptionHandlerExceptionResolver resolver = new ExceptionHandlerExceptionResolver();
    resolver.setApplicationContext(ctx);
    resolver.setMessageConverters(List.of(new MappingJackson2HttpMessageConverter()));
    resolver.afterPropertiesSet();
    return resolver;
  }
}