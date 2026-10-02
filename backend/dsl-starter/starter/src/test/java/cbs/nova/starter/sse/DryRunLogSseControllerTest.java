package cbs.nova.starter.sse;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.properties.CbsSseProperties;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.converter.DslExceptionMapper;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

class DryRunLogSseControllerTest {

  private static final DslExceptionMapper NO_OP_MAPPER = (ex, req) -> null;

  private final DryRunLogSseService sseService = new DryRunLogSseService(
          new CbsSseProperties(1));
  private final DryRunLogSseService sseServiceHighCap = new DryRunLogSseService(
          new CbsSseProperties(500));
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DryRunLogSseController controller = new DryRunLogSseController(sseService);
    mockMvc = MockMvcBuilders.standaloneSetup(controller)
            .setHandlerExceptionResolvers(buildExceptionResolver(NO_OP_MAPPER))
            .build();
  }

  @Test
  void malformedTraceIdReturns404() throws Exception {
    mockMvc.perform(get("/api/dsl/dry-run/bad!trace/logs")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isNotFound());

    Assertions.assertThat(sseService.subscriberCount("bad!trace")).isEqualTo(0);
  }

  @Test
  void validTraceIdOpensSseStreamAndSendsConnectedComment() throws Exception {
    mockMvc.perform(get("/api/dsl/dry-run/trace-1/logs")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.TEXT_EVENT_STREAM_VALUE))
            .andExpect(content().string(containsString(":connected")));

    Assertions.assertThat(sseService.subscriberCount("trace-1")).isEqualTo(1);
  }

  @Test
  void overCapReturns429WithErrorResponseShapeAndCreatesNoEmitter() throws Exception {
    // cap=1 → second distinct trace id → 429.
    mockMvc.perform(get("/api/dsl/dry-run/trace-a/logs")
            .accept(MediaType.TEXT_EVENT_STREAM))
            .andExpect(status().isOk());

    mockMvc.perform(get("/api/dsl/dry-run/trace-b/logs")
            .accept(MediaType.TEXT_EVENT_STREAM, MediaType.APPLICATION_JSON))
            .andExpect(status().isTooManyRequests())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.code").value("SUBSCRIBER_CAP_EXCEEDED"))
            .andExpect(jsonPath("$.message", containsString("cap=1")));

    Assertions.assertThat(sseService.subscriberCount("trace-b")).isEqualTo(0);
  }

  @Test
  void reSubscribeSameIdIsNotCountedAgainstCap() throws Exception {
    DryRunLogSseController controller = new DryRunLogSseController(sseServiceHighCap);
    MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
            .setHandlerExceptionResolvers(buildExceptionResolver(NO_OP_MAPPER))
            .build();

    mvc.perform(get("/api/dsl/dry-run/trace-x/logs")
            .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isOk());
    mvc.perform(get("/api/dsl/dry-run/trace-x/logs")
            .accept(MediaType.TEXT_EVENT_STREAM)).andExpect(status().isOk());

    Assertions.assertThat(sseServiceHighCap.activeSubscriberCount()).isEqualTo(1);
  }

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