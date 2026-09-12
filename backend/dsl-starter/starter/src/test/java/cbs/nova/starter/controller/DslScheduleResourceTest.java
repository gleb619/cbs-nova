package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.AuditTestSupport;
import cbs.nova.starter.config.router.DslScheduleRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.exception.ScheduleConflictException;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleResponse;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import cbs.nova.starter.service.DslScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

class DslScheduleResourceTest {

  private final DslScheduleService service = mock(DslScheduleService.class);
  private final ObjectMapper objectMapper = new ObjectMapper();
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DslScheduleHandler handler = new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.emptyProvider());
    DslScheduleRouterConfiguration router = new DslScheduleRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.dslScheduleRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void createWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    when(service.create(any())).thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));
    mockMvc = mockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isCreated());

    var result = audit.repository().search(null, 0, 10);
    assertThat(result.total()).isEqualTo(1);
    var row = result.items().get(0);
    assertThat(row.action()).isEqualTo("SCHEDULE_CREATE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("sched-A");
    assertThat(row.actor()).isEqualTo("anonymous");
  }

  @Test
  void createWritesAuditRowOnFailure() throws Exception {
    var audit = AuditTestSupport.h2();
    when(service.create(any())).thenThrow(new DefinitionNotFoundException("ghost"));
    mockMvc = mockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"ghost\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isNotFound());

    var result = audit.repository().search(null, 0, 10);
    assertThat(result.total()).isEqualTo(1);
    var row = result.items().get(0);
    assertThat(row.action()).isEqualTo("SCHEDULE_CREATE");
    assertThat(row.outcome()).isEqualTo("FAILURE");
    assertThat(row.target()).isEqualTo("ghost");
  }

  @Test
  void deleteWritesAuditRowOnSuccess() throws Exception {
    var audit = AuditTestSupport.h2();
    mockMvc = mockMvc(new DslScheduleHandler(service, objectMapper,
            AuditTestSupport.providerOf(audit.service())));

    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk());

    var result = audit.repository().search(null, 0, 10);
    assertThat(result.total()).isEqualTo(1);
    var row = result.items().get(0);
    assertThat(row.action()).isEqualTo("SCHEDULE_DELETE");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.target()).isEqualTo("A");
  }

  private MockMvc mockMvc(DslScheduleHandler handler) {
    DslScheduleRouterConfiguration router = new DslScheduleRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    return MockMvcBuilders.routerFunctions(router.dslScheduleRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void listReturnsPaginatedEnvelope() throws Exception {
    when(service.list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", "daily", null, false),
            new ScheduleSummary("sched-B", "B", "0 10 * * *", "UTC", "hourly", null, false)));

    mockMvc.perform(get("/api/dsl/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].scheduleId").value("sched-A"))
            .andExpect(jsonPath("$.items[0].definition").value("A"))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listHonoursOffsetAndLimit() throws Exception {
    when(service.list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", "daily", null, false),
            new ScheduleSummary("sched-B", "B", "0 10 * * *", "UTC", "hourly", null, false)));

    mockMvc.perform(get("/api/dsl/schedules").param("limit", "1").param("offset", "1"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].scheduleId").value("sched-B"))
            .andExpect(jsonPath("$.total").value(2))
            .andExpect(jsonPath("$.offset").value(1))
            .andExpect(jsonPath("$.limit").value(1));
  }

  @Test
  void createReturns201OnSuccess() throws Exception {
    when(service.create(any())).thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\",\"timezone\":\"UTC\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scheduleId").value("sched-A"))
            .andExpect(jsonPath("$.definition").value("A"));
  }

  @Test
  void createReturns400ForInvalidBody() throws Exception {
    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("not json"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }

  @Test
  void createReturns404ForUnknownDefinition() throws Exception {
    when(service.create(any())).thenThrow(new DefinitionNotFoundException("Missing"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"Missing\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
  }

  @Test
  void createReturns409ForDuplicateSchedule() throws Exception {
    when(service.create(any())).thenThrow(new ScheduleConflictException("sched-A"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
  }

  @Test
  void deleteReturns200AndCallsService() throws Exception {
    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    verify(service).delete("A");
  }

  @Test
  void deletePropagatesIllegalArgumentAsBadRequest() throws Exception {
    doThrow(new IllegalArgumentException("Invalid definition name")).when(service).delete("A");

    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
  }
}
