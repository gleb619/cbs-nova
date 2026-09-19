package cbs.nova.starter.vhs.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.properties.CbsVhsProperties;
import cbs.nova.starter.config.router.VhsManagementRouterConfiguration;
import cbs.nova.starter.controller.DslExceptionHandler;
import cbs.nova.starter.controller.VhsManagementHandler;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Integration tests for the VHS tape management API (T559): listing and deleting tapes, and
 * downloading raw tape bytes.
 */
class VhsManagementIntegrationTest {

  private static final DateTimeFormatter TIMESTAMP_FORMATTER = DateTimeFormatter
          .ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

  @TempDir
  Path tempDir;

  private MockMvc mockMvc;
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    objectMapper = JsonMapper.builder().build();
    CbsVhsProperties properties = new CbsVhsProperties(
            true, List.of("*"), CbsVhsProperties.SinkType.local,
            new CbsVhsProperties.LocalSink(tempDir, 0, 0L),
            new CbsVhsProperties.Scrub(false, List.of(), null, null));

    LocalFileTapeStore store = new LocalFileTapeStore(properties, objectMapper);
    ObjectProvider<VhsReplayJob> replayProvider = mock(ObjectProvider.class);
    VhsManagementService service = new VhsManagementService(store, replayProvider);
    VhsManagementHandler handler = new VhsManagementHandler(service, null, objectMapper);
    VhsManagementRouterConfiguration router = new VhsManagementRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.vhsManagementRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void listReturnsEmptyEnvelopeWhenNoTapes() throws Exception {
    mockMvc.perform(get("/api/v1/vhs/tapes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listReturnsFixtureTapes() throws Exception {
    writeTape("run-a", "Ping", "corr-1", Instant.parse("2026-09-19T10:00:00Z"), 2);
    writeTape("run-b", "Pong", "corr-2", Instant.parse("2026-09-19T11:00:00Z"), 1);

    mockMvc.perform(get("/api/v1/vhs/tapes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.items[0].run_id").value("run-b"))
            .andExpect(jsonPath("$.items[0].route").value("Pong"))
            .andExpect(jsonPath("$.items[1].run_id").value("run-a"))
            .andExpect(jsonPath("$.items[1].event_count").value(2));
  }

  @Test
  void deleteRemovesTapeAndSubsequentListOmitsIt() throws Exception {
    writeTape("run-del", "Ping", "corr-del", Instant.now(), 1);

    mockMvc.perform(delete("/api/v1/vhs/tapes/run-del"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    mockMvc.perform(get("/api/v1/vhs/tapes"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.items").isEmpty());
  }

  @Test
  void downloadReturnsRawTapeBytes() throws Exception {
    writeTape("run-dl", "Ping", "corr-dl", Instant.now(), 1);

    MvcResult result = mockMvc.perform(get("/api/v1/vhs/tapes/run-dl"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/x-jsonl"))
            .andReturn();

    String body = result.getResponse().getContentAsString();
    assertThat(body).contains("\"source_run_id\":\"run-dl\"");
    assertThat(body).contains("\"event_type\":\"tape_closed\"");
  }

  @Test
  void downloadMissingTapeReturns404() throws Exception {
    mockMvc.perform(get("/api/v1/vhs/tapes/no-such-run"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  private void writeTape(String runId, String route, String correlationId, Instant recordedAt,
          int eventCount) throws IOException {
    String timestamp = TIMESTAMP_FORMATTER.format(recordedAt);
    String safeRunId = runId.replace('/', '_');
    String safeCorr = correlationId == null ? "none" : correlationId.replace('/', '_');
    Path file = tempDir.resolve(timestamp + "_" + safeRunId + "_" + safeCorr + ".vhs.jsonl");

    Map<String, Object> header = new HashMap<>();
    header.put("vhs_tape_format_version", "1.0.0");
    header.put("schema_version", "1");
    header.put("recorded_at", recordedAt.toString());
    header.put("source_run_id", runId);
    header.put("correlation_id", correlationId);
    header.put("route", route);
    Files.writeString(file, objectMapper.writeValueAsString(header) + "\n");

    for (int i = 0; i < eventCount; i++) {
      Map<String, Object> event = new HashMap<>();
      event.put("schema_version", "1");
      event.put("event_index", i);
      event.put("event_type", "call_start");
      event.put("timestamp", recordedAt.toString());
      event.put("relative_ms", i * 10L);
      event.put("call_metadata", Map.of("call_id", "call_" + i, "type", "helper", "target", route,
              "operation", "get"));
      event.put("input", Map.of());
      event.put("output", null);
      event.put("timing", Map.of("started_at", recordedAt.toString(), "finished_at",
              recordedAt.toString(), "duration_ms", 1L));
      event.put("correlation_id", correlationId);
      event.put("metadata", Map.of());
      Files.writeString(file, objectMapper.writeValueAsString(event) + "\n",
              java.nio.file.StandardOpenOption.APPEND);
    }

    Map<String, Object> trailer = new HashMap<>();
    trailer.put("schema_version", "1");
    trailer.put("event_index", eventCount);
    trailer.put("event_type", "tape_closed");
    trailer.put("timestamp", Instant.now().toString());
    trailer.put("relative_ms", eventCount * 10L);
    trailer.put("output", Map.of("event_count", eventCount));
    trailer.put("timing", new java.util.HashMap<String, Object>() {
      {
        put("started_at", null);
        put("finished_at", null);
        put("duration_ms", null);
      }
    });
    trailer.put("correlation_id", correlationId);
    trailer.put("metadata", Map.of());
    Files.writeString(file, objectMapper.writeValueAsString(trailer) + "\n",
            java.nio.file.StandardOpenOption.APPEND);
  }
}
