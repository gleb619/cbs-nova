package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import cbs.nova.starter.config.router.VhsManagementRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.vhs.loadtest.VhsLoadTest;
import cbs.nova.starter.vhs.loadtest.VhsLoadTestException;
import cbs.nova.starter.vhs.loadtest.VhsLoadTestReport;
import cbs.nova.starter.vhs.management.ReplayRunRequest;
import cbs.nova.starter.vhs.management.ReplayRunResponse;
import cbs.nova.starter.vhs.management.TapeSummary;
import cbs.nova.starter.vhs.management.VhsManagementService;
import cbs.nova.starter.vhs.replay.ReplayMode;
import cbs.nova.starter.vhs.replay.VhsReplayReport;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Direct unit spec for {@link VhsManagementHandler}. Pins the HTTP-layer contract of the VHS tape
 * management surface: envelope shape, status codes, response bodies, and the optional
 * {@link VhsLoadTest} bean boundary.
 *
 * <p>
 * Tier: unit. Mocks {@link VhsManagementService} and {@link VhsLoadTest} at the tape-sink /
 * replay-engine boundary (mirrors the boundary used in {@code VhsManagementServiceTest}). Exercises
 * the handler through MockMvc with the real {@link VhsManagementRouterConfiguration} routes plus
 * the production {@link DslExceptionHandler} advice so exception-mapping stays consistent.
 */
class VhsManagementHandlerTest {

  private static final Instant RECORDED_AT = Instant.parse("2026-09-27T10:00:00Z");

  private final VhsManagementService service = mock(VhsManagementService.class);
  private final VhsLoadTest loadTest = mock(VhsLoadTest.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static RequestBodyLimitFilter requestBodyLimitFilter;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    requestBodyLimitFilter = new RequestBodyLimitFilter(new CbsNovaWebProperties(0L));
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.emptyProvider()));
  }

  // ----- list ---------------------------------------------------------------

  @Test
  void listReturnsPageResponseEnvelopeByDefault() throws Exception {
    when(service.listTapes()).thenReturn(List.of(
            summary("run-a", "Ping", "corr-a", 2),
            summary("run-b", "Pong", "corr-b", 1)));

    mockMvc.perform(get("/api/v1/vhs/tapes"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items[0].filename").value("run-a.vhs.jsonl"))
            .andExpect(jsonPath("$.items[0].run_id").value("run-a"))
            .andExpect(jsonPath("$.items[0].correlation_id").value("corr-a"))
            .andExpect(jsonPath("$.items[0].route").value("Ping"))
            .andExpect(jsonPath("$.items[0].size_bytes").value(1024))
            .andExpect(jsonPath("$.items[0].event_count").value(2))
            .andExpect(jsonPath("$.items[1].run_id").value("run-b"))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listHonoursLimitAndOffset() throws Exception {
    when(service.listTapes()).thenReturn(List.of(
            summary("run-a", "Ping", "corr-a", 1),
            summary("run-b", "Pong", "corr-b", 1),
            summary("run-c", "Foo", "corr-c", 1)));

    mockMvc.perform(get("/api/v1/vhs/tapes").param("limit", "2").param("offset", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].run_id").value("run-b"))
            .andExpect(jsonPath("$.items[1].run_id").value("run-c"))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(2));
  }

  @Test
  void listReturns400ForNonIntegerLimit() throws Exception {
    mockMvc.perform(get("/api/v1/vhs/tapes").param("limit", "abc"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // ----- download -----------------------------------------------------------

  @Test
  void downloadReturnsTapeBodyWithJsonlContentType() throws Exception {
    String body = "{\"event_type\":\"tape_closed\"}\n";
    when(service.openTapeStream("run-dl")).thenReturn(Optional.of(new ByteArrayInputStream(
            body.getBytes(java.nio.charset.StandardCharsets.UTF_8))));

    MvcResult result = mockMvc.perform(get("/api/v1/vhs/tapes/run-dl"))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/x-jsonl"))
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).isEqualTo(body);
    verify(service).openTapeStream("run-dl");
  }

  @Test
  void downloadReturns404WhenTapeMissing() throws Exception {
    when(service.openTapeStream("no-such-run")).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/vhs/tapes/no-such-run"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Tape not found: no-such-run"));
  }

  // ----- delete -------------------------------------------------------------

  @Test
  void deleteReturns200AndTrueWhenTapeExists() throws Exception {
    when(service.deleteTape("run-del")).thenReturn(true);

    mockMvc.perform(delete("/api/v1/vhs/tapes/run-del"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    verify(service).deleteTape("run-del");
  }

  @Test
  void deleteIsIdempotentAndReturns200WithFalseWhenTapeMissing() throws Exception {
    // Pinned current behavior: handler returns 200 + {"deleted": false} when the service
    // reports the tape was not found. Delete is non-404 / non-204 here on purpose — the
    // existing semantics are "best-effort idempotent" with a boolean outcome field.
    when(service.deleteTape("ghost")).thenReturn(false);

    mockMvc.perform(delete("/api/v1/vhs/tapes/ghost"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(false));
  }

  // ----- replay -------------------------------------------------------------

  @Test
  void replayReturns202WhenServiceReportsAccepted() throws Exception {
    ReplayRunResponse response = new ReplayRunResponse(
            "replay-abc", "accepted", "dry-run", "exact",
            "/api/v1/vhs/tapes/run-rep/replay/replay-abc", null);
    when(service.startReplay(eq("run-rep"), any())).thenReturn(Optional.of(response));

    mockMvc.perform(post("/api/v1/vhs/tapes/run-rep/replay")
            .contentType("application/json")
            .content("{\"target\":\"dry-run\",\"mode\":\"exact\",\"speed\":1.0,\"concurrency\":1,"
                    + "\"copies\":1}"))
            .andExpect(status().isAccepted())
            .andExpect(jsonPath("$.replay_run_id").value("replay-abc"))
            .andExpect(jsonPath("$.status").value("accepted"))
            .andExpect(jsonPath("$.target").value("dry-run"))
            .andExpect(jsonPath("$.mode").value("exact"))
            .andExpect(jsonPath("$.status_url").value(
                    "/api/v1/vhs/tapes/run-rep/replay/replay-abc"));
  }

  @Test
  void replayReturns200WhenServiceReportsNonAcceptedStatus() throws Exception {
    ReplayRunResponse response = new ReplayRunResponse(
            "replay-done", "completed", "dry-run", "exact", null,
            shortReport());
    when(service.startReplay(eq("run-rep"), any())).thenReturn(Optional.of(response));

    mockMvc.perform(post("/api/v1/vhs/tapes/run-rep/replay"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.replay_run_id").value("replay-done"))
            .andExpect(jsonPath("$.status").value("completed"))
            .andExpect(jsonPath("$.report.totalCalls").value(1));
  }

  @Test
  void replayReturns503WhenServiceReturnsEmptyOptional() throws Exception {
    // Both "replay disabled" and "tape not found" collapse into Optional.empty() in the
    // service; handler surfaces 503 + a static error map regardless of which branch fired.
    when(service.startReplay(eq("missing"), any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/v1/vhs/tapes/missing/replay")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value("VHS replay is not enabled"));
  }

  @Test
  void replayFallsBackToDefaultRequestWhenBodyMissing() throws Exception {
    ReplayRunResponse response = new ReplayRunResponse(
            "replay-x", "accepted", "dry-run", "exact", null, null);
    when(service.startReplay(eq("run-rep"), any())).thenReturn(Optional.of(response));

    mockMvc.perform(post("/api/v1/vhs/tapes/run-rep/replay"))
            .andExpect(status().isAccepted());

    verify(service).startReplay(eq("run-rep"), eq(new ReplayRunRequest(null, null, null, null,
            null)));
  }

  // ----- loadtest ----------------------------------------------------------

  @Test
  void loadtestReturns503WhenBeanUnavailable() throws Exception {
    mockMvc = buildMockMvc(new VhsManagementHandler(service, null, objectMapper,
            AuditTestSupport.emptyProvider()));

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.error").value(
                    "VHS load-test is not enabled (cbs.vhs.replay.enabled must be true)"));
  }

  @Test
  void loadtestReturnsReportOnSuccessAndAwaitsInline() throws Exception {
    // Pinned current behavior: loadTest.run(...) is invoked synchronously on the request
    // thread; the response body is the report itself, not a status handle. The call returns
    // only after replay completes (no async offload). Default param values from the handler
    // (tapes=**/*.vhs.jsonl, target=dry-run, speed=1.0, concurrency=4, duration=0) are
    // forwarded when the body is empty/missing.
    VhsLoadTestReport report = new VhsLoadTestReport(
            "dry-run", 1, 2L, 2L, 0L, 1234L,
            List.of(new VhsLoadTestReport.TapePercentileSummary(
                    "run-a", 2L, 2L, 0L, 10L, 50L, 100L, 0.0)),
            List.of(shortReport()));
    when(loadTest.run(eq("**/*.vhs.jsonl"), eq("dry-run"), eq(1.0), eq(4), eq(0L)))
            .thenReturn(report);

    MvcResult result = mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").value("dry-run"))
            .andExpect(jsonPath("$.tapesLoaded").value(1))
            .andExpect(jsonPath("$.totalCalls").value(2))
            .andExpect(jsonPath("$.successfulCalls").value(2))
            .andExpect(jsonPath("$.failedCalls").value(0))
            .andExpect(jsonPath("$.wallClockMs").value(1234))
            .andExpect(jsonPath("$.tapeSummaries[0].tapeName").value("run-a"))
            .andExpect(jsonPath("$.tapeSummaries[0].p50Ms").value(10))
            .andExpect(jsonPath("$.tapeSummaries[0].errorRatePct").value(0.0))
            .andReturn();

    assertThat(result.getResponse().getContentAsString()).contains("\"tapeName\":\"run-a\"");
    verify(loadTest).run("**/*.vhs.jsonl", "dry-run", 1.0, 4, 0L);
  }

  @Test
  void loadtestForwardsExplicitParamsAndReturnsReport() throws Exception {
    VhsLoadTestReport report = new VhsLoadTestReport(
            "local", 2, 10L, 9L, 1L, 5000L, List.of(), List.of());
    when(loadTest.run(eq("/tmp/tapes/*.vhs.jsonl"), eq("local"), eq(2.5), eq(8), eq(60_000L)))
            .thenReturn(report);

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{\"tapes\":\"/tmp/tapes/*.vhs.jsonl\",\"target\":\"local\","
                    + "\"speed\":2.5,\"concurrency\":8,\"duration\":60000}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.target").value("local"))
            .andExpect(jsonPath("$.tapesLoaded").value(2));

    verify(loadTest).run("/tmp/tapes/*.vhs.jsonl", "local", 2.5, 8, 60_000L);
  }

  @Test
  void loadtestReturns400OnBadParams() throws Exception {
    // Pinned current behavior: VhsLoadTestException (e.g. empty glob) maps to 400 with the
    // VHS_LOAD_TEST_ERROR code and the exception message carried through verbatim.
    doThrow(new VhsLoadTestException("No tape files found matching: /no/such/dir"))
            .when(loadTest).run(any(), any(), any(Double.class), any(Integer.class),
                    any(Long.class));

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{\"tapes\":\"/no/such/dir\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VHS_LOAD_TEST_ERROR"))
            .andExpect(jsonPath("$.message").value("No tape files found matching: /no/such/dir"));
  }

  // ----- audit (T776) -------------------------------------------------------

  @Test
  void deleteWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    when(service.deleteTape("run-del")).thenReturn(true);

    mockMvc.perform(delete("/api/v1/vhs/tapes/run-del"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_TAPE_DELETE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("run-del");
  }

  @Test
  void deleteTapeNotFoundWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    when(service.deleteTape("ghost")).thenReturn(false);

    mockMvc.perform(delete("/api/v1/vhs/tapes/ghost"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(false));

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_TAPE_DELETE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("ghost");
  }

  @Test
  void replayAcceptedWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    ReplayRunResponse response = new ReplayRunResponse(
            "replay-abc", "accepted", "dry-run", "exact",
            "/api/v1/vhs/tapes/run-rep/replay/replay-abc", null);
    when(service.startReplay(eq("run-rep"), any())).thenReturn(Optional.of(response));

    mockMvc.perform(post("/api/v1/vhs/tapes/run-rep/replay")
            .contentType("application/json")
            .content("{\"target\":\"dry-run\",\"mode\":\"exact\",\"speed\":1.0,\"concurrency\":1,"
                    + "\"copies\":1}"))
            .andExpect(status().isAccepted());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_TAPE_REPLAY");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("run-rep");
  }

  @Test
  void replayReturns503WhenServiceReturnsEmptyWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    when(service.startReplay(eq("missing"), any())).thenReturn(Optional.empty());

    mockMvc.perform(post("/api/v1/vhs/tapes/missing/replay")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isServiceUnavailable());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_TAPE_REPLAY");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("missing");
  }

  @Test
  void loadtestSuccessWritesAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    VhsLoadTestReport report = new VhsLoadTestReport(
            "dry-run", 1, 2L, 2L, 0L, 1234L, List.of(), List.of());
    when(loadTest.run(eq("**/*.vhs.jsonl"), eq("dry-run"), eq(1.0), eq(4), eq(0L)))
            .thenReturn(report);

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isOk());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_LOAD_TEST");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("-");
  }

  @Test
  void loadtestBadParamsWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, loadTest, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    doThrow(new VhsLoadTestException("No tape files found matching: /no/such/dir"))
            .when(loadTest).run(any(), any(), any(Double.class), any(Integer.class),
                    any(Long.class));

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{\"tapes\":\"/no/such/dir\"}"))
            .andExpect(status().isBadRequest());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_LOAD_TEST");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void loadtestBeanMissingWritesFailureAuditRow() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new VhsManagementHandler(service, null, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/v1/vhs/loadtest")
            .contentType("application/json")
            .content("{}"))
            .andExpect(status().isServiceUnavailable());

    var rows = audit.service().search(null, 0, 10).items();
    assertThat(rows).hasSize(1);
    var row = rows.get(0);
    assertThat(row.action()).isEqualTo("VHS_LOAD_TEST");
    assertThat(row.outcome()).isEqualTo("FAILURE");
  }

  @Test
  void vhsHandlerSucceedsWithoutAuditBean() throws Exception {
    // Default @BeforeEach wiring uses the empty provider; pins the no-audit-bean path.
    when(service.deleteTape("run-a")).thenReturn(true);

    mockMvc.perform(delete("/api/v1/vhs/tapes/run-a"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    ReplayRunResponse response = new ReplayRunResponse(
            "replay-z", "accepted", "dry-run", "exact", null, null);
    when(service.startReplay(eq("run-a"), any())).thenReturn(Optional.of(response));

    mockMvc.perform(post("/api/v1/vhs/tapes/run-a/replay"))
            .andExpect(status().isAccepted());
  }

  // ----- helpers ------------------------------------------------------------

  private static MockMvc buildMockMvc(VhsManagementHandler handler) {
    VhsManagementRouterConfiguration router = new VhsManagementRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders
            .routerFunctions(router.vhsManagementRouter(handler, requestBodyLimitFilter))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  private static TapeSummary summary(String runId, String route, String correlationId,
          long eventCount) {
    return new TapeSummary(runId + ".vhs.jsonl", RECORDED_AT, runId, correlationId, route, 1024L,
            eventCount);
  }

  private static VhsReplayReport shortReport() {
    return new VhsReplayReport(ReplayMode.exact, 1, 1L, 1L, 0L, 0, 100L,
            List.of(), List.of());
  }
}
