package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import cbs.nova.starter.config.router.DslScheduleRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleResponse;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import cbs.nova.starter.model.ScheduleModels.UpdateScheduleRequest;
import cbs.nova.starter.service.DslScheduleService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Direct unit spec for {@link DslScheduleHandler}. Pins the HTTP-layer contract: status codes, body
 * mapping, audit invocation and best-effort audit behaviour for the absent-bean path.
 *
 * <p>
 * Tier: unit. Mocks {@link DslScheduleService} and exercises the handler through MockMvc with the
 * real {@link DslScheduleRouterConfiguration} routes plus the production
 * {@link DslExceptionHandler} advice so
 * {@link IllegalArgumentException}/{@link DefinitionNotFoundException}/
 * {@link ScheduleConflictException} map to 400/404/409 as documented.
 */
class DslScheduleHandlerTest {

  private final DslScheduleService service = mock(DslScheduleService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private static RequestBodyLimitFilter requestBodyLimitFilter;
private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    requestBodyLimitFilter = new RequestBodyLimitFilter(new CbsNovaWebProperties(0L));
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.emptyProvider()));
  }

  // ----- create -------------------------------------------------------------

  @Test
  void createReturns201WithDtoShapeOnSuccess() throws Exception {
    when(service.create(any())).thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scheduleId").value("sched-A"))
            .andExpect(jsonPath("$.definition").value("A"))
            .andExpect(jsonPath("$.cron").value("0 9 * * *"));
  }

  @Test
  void createReturns404ForUnknownDefinition() throws Exception {
    when(service.create(any())).thenThrow(new DefinitionNotFoundException("ghost"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"ghost\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void createReturns409ForSchedDefinitionCollision() throws Exception {
    when(service.create(any())).thenThrow(new ScheduleConflictException("sched-A"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void createReturns400ForInvalidTimezone() throws Exception {
    when(service.create(any()))
            .thenThrow(new IllegalArgumentException("Invalid timezone: Mars/Olympus"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\",\"timezone\":\"Mars/Olympus\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("Invalid timezone: Mars/Olympus"));
  }

  @Test
  void createReturns400ForBlankCron() throws Exception {
    when(service.create(any())).thenThrow(new IllegalArgumentException("cron is required"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void createReturns400WhenBodyMissing() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // ----- list ---------------------------------------------------------------

  @Test
  void listReturnsPageResponseEnvelopeByDefault() throws Exception {
    when(service.list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", "daily", null, false),
            new ScheduleSummary("sched-B", "B", "0 10 * * *", "UTC", "hourly", null, true)));

    mockMvc.perform(get("/api/dsl/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].scheduleId").value("sched-A"))
            .andExpect(jsonPath("$.items[0].definition").value("A"))
            .andExpect(jsonPath("$.items[0].paused").value(false))
            .andExpect(jsonPath("$.items[1].scheduleId").value("sched-B"))
            .andExpect(jsonPath("$.items[1].paused").value(true))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listHonoursLimitAndOffset() throws Exception {
    when(service.list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", null, null, false),
            new ScheduleSummary("sched-B", "B", "0 10 * * *", "UTC", null, null, false),
            new ScheduleSummary("sched-C", "C", "0 11 * * *", "UTC", null, null, false)));

    mockMvc.perform(get("/api/dsl/schedules").param("limit", "2").param("offset", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].scheduleId").value("sched-B"))
            .andExpect(jsonPath("$.items[1].scheduleId").value("sched-C"))
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(2));
  }

  // ----- delete -------------------------------------------------------------

  @Test
  void deleteReturns200AndCallsService() throws Exception {
    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    verify(service).delete("A");
  }

  @Test
  void deleteReturns400ForInvalidDefinitionName() throws Exception {
    doThrow(new IllegalArgumentException(
            "Invalid definition name: must match ^[A-Za-z0-9._-]{1,120}$"))
            .when(service).delete("bad$name");

    mockMvc.perform(delete("/api/dsl/schedules/bad$name"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  // ----- pause / resume (T507) ---------------------------------------------

  @Test
  void pauseReturns200AndForwardsReason() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules/A/pause")
            .contentType("application/json")
            .content("{\"reason\":\"maintenance\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paused").value(true));

    verify(service).pause("A", "maintenance");
  }

  @Test
  void pauseReturns200WithNoBody() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules/A/pause"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paused").value(true));

    verify(service).pause(eq("A"), any());
  }

  @Test
  void pauseReturns404ForMissingSchedule() throws Exception {
    doThrow(new DefinitionNotFoundException("ghost")).when(service).pause(eq("ghost"), any());

    mockMvc.perform(post("/api/dsl/schedules/ghost/pause"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void pauseWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/dsl/schedules/A/pause"))
            .andExpect(status().isOk());

    var row = audit.service().search(null, 0, 10).items().get(0);
    assertThat(row.action()).isEqualTo("schedule.paused");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("A");
  }

  @Test
  void resumeReturns200AndForwardsReason() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules/A/resume")
            .contentType("application/json")
            .content("{\"reason\":\"back online\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumed").value(true));

    verify(service).resume("A", "back online");
  }

  @Test
  void resumeReturns404ForMissingSchedule() throws Exception {
    doThrow(new DefinitionNotFoundException("ghost")).when(service).resume(eq("ghost"), any());

    mockMvc.perform(post("/api/dsl/schedules/ghost/resume"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void resumeWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/dsl/schedules/A/resume"))
            .andExpect(status().isOk());

    var row = audit.service().search(null, 0, 10).items().get(0);
    assertThat(row.action()).isEqualTo("schedule.resumed");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("A");
  }

  // ----- update -------------------------------------------------------------

  @Test
  void updateReturns200AndDelegatesToServiceUpdate() throws Exception {
    mockMvc.perform(patch("/api/dsl/schedules/A")
            .contentType("application/json")
            .content("{\"cron\":\"30 14 * * 1\",\"timezone\":\"Asia/Almaty\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true));

    verify(service).update(eq("A"), any(UpdateScheduleRequest.class));
  }

  @Test
  void updateReturns404ForMissingSchedule() throws Exception {
    doThrow(new DefinitionNotFoundException("ghost")).when(service).update(eq("ghost"),
            any(UpdateScheduleRequest.class));

    mockMvc.perform(patch("/api/dsl/schedules/ghost")
            .contentType("application/json")
            .content("{\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void updateReturns400ForInvalidTimezone() throws Exception {
    doThrow(new IllegalArgumentException("Invalid timezone: Mars/Olympus"))
            .when(service).update(eq("A"), any(UpdateScheduleRequest.class));

    mockMvc.perform(patch("/api/dsl/schedules/A")
            .contentType("application/json")
            .content("{\"cron\":\"0 9 * * *\",\"timezone\":\"Mars/Olympus\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void updateReturns400WhenBodyMissing() throws Exception {
    mockMvc.perform(patch("/api/dsl/schedules/A"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void updateWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(patch("/api/dsl/schedules/A")
            .contentType("application/json")
            .content("{\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\"}"))
            .andExpect(status().isOk());

    var row = audit.service().search(null, 0, 10).items().get(0);
    assertThat(row.action()).isEqualTo("schedule.updated");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("A");
  }

  // ----- audit() best-effort behaviour --------------------------------------

  @Test
  void createSucceedsWithoutAuditBeanAndDoesNotThrow() throws Exception {
    // emptyProvider() returns null from getIfAvailable(); audit(...) must swallow the absence
    // rather than NPE or propagate to the client. This is the no-DataSource deployment path.
    when(service.create(any())).thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scheduleId").value("sched-A"));
  }

  @Test
  void pauseAndResumeSucceedWithoutAuditBean() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules/A/pause"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.paused").value(true));

    mockMvc.perform(post("/api/dsl/schedules/A/resume"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resumed").value(true));

    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    mockMvc.perform(patch("/api/dsl/schedules/A")
            .contentType("application/json")
            .content("{\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true));
  }

  @Test
  void deleteWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk());

    var row = audit.service().search(null, 0, 10).items().get(0);
    assertThat(row.action()).isEqualTo("SCHEDULE_DELETE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("A");
  }

  @Test
  void createWritesAuditRowOnSuccessWithDefinitionAndCronDetails() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = buildMockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));
    when(service.create(any())).thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isCreated());

    var row = audit.service().search(null, 0, 10).items().get(0);
    assertThat(row.action()).isEqualTo("SCHEDULE_CREATE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("sched-A");
    var details = objectMapper.readValue(row.detailsJson(), java.util.Map.class);
    assertThat(details).containsEntry("definition", "A").containsEntry("cron", "0 9 * * *");
  }

  // ----- helpers ------------------------------------------------------------

  private static MockMvc buildMockMvc(DslScheduleHandler handler) {
    DslScheduleRouterConfiguration router = new DslScheduleRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders.routerFunctions(router.dslScheduleRouter(handler, requestBodyLimitFilter))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }
}
