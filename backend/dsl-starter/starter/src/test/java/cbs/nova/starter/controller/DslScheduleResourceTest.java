package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
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

import cbs.nova.starter.config.DslScheduleRouterConfiguration;
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
    DslScheduleHandler handler = new DslScheduleHandler(service, objectMapper);
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
  void listReturnsServiceSummaries() throws Exception {
    when(service.list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", "daily", null, false)));

    mockMvc.perform(get("/api/dsl/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$", hasSize(1)))
            .andExpect(jsonPath("$[0].scheduleId").value("sched-A"))
            .andExpect(jsonPath("$[0].definition").value("A"));
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
