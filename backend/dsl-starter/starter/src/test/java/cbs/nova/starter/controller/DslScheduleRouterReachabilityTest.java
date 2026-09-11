package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.router.DslScheduleRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleResponse;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import cbs.nova.starter.service.DslScheduleService;
import io.temporal.client.schedules.ScheduleClient;
import java.util.List;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;
import tools.jackson.databind.ObjectMapper;

/**
 * Reachability regression for T415. {@link DslScheduleRouterConfiguration} was previously never
 * imported by {@link cbs.nova.starter.config.router.DslRouterConfiguration}, so
 * {@code GET /api/dsl/schedules} resolved to 404 and the OpenAPI document omitted the routes
 * entirely. This test loads the router config inside a minimal Spring context together with a stub
 * {@link ScheduleClient} (which is what {@code DslScheduleHandler} and
 * {@code DslScheduleRouterConfiguration} are conditional on), proves the {@link RouterFunction}
 * bean materialises, and asserts the {@code GET} endpoint is reachable — not 404.
 *
 * <p>
 * Mirrors {@code DslEventHandlerTest}: same MockMvc pattern, same exception advice setup, same
 * {@code JacksonJsonHttpMessageConverter} wiring.
 */
class DslScheduleRouterReachabilityTest {

  private AnnotationConfigApplicationContext context;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    context = new AnnotationConfigApplicationContext();
    context.register(StubBeans.class);
    // DslScheduleHandler is @Component @ConditionalOnBean(ScheduleClient.class). Registering the
    // bean directly via the handler constructor (no component scan) lets the test bypass the
    // conditional while still exercising the real handler + router wiring.
    DslScheduleService service = mock(DslScheduleService.class);
    context.registerBean(DslScheduleService.class, () -> service);
    context.registerBean("scheduleClient", ScheduleClient.class, () -> mock(ScheduleClient.class));
    DslScheduleHandler handler = new DslScheduleHandler(service, new ObjectMapper());
    context.registerBean(DslScheduleHandler.class, () -> handler);
    context.register(DslScheduleRouterConfiguration.class);
    context.refresh();

    mockMvc = MockMvcBuilders.routerFunctions(routerFunction())
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver())
            .build();
  }

  @AfterEach
  void tearDown() {
    if (context != null) {
      context.close();
    }
  }

  @Test
  void routerFunctionBeanIsPublishedWhenScheduleClientIsPresent() {
    assertThat(context.getBeansOfType(RouterFunction.class))
            .as("DslScheduleRouterConfiguration must publish a RouterFunction bean "
                    + "once ScheduleClient is available")
            .isNotEmpty();
    assertThat(context.getBeanNamesForType(RouterFunction.class))
            .as("at least one RouterFunction<ServerResponse> bean must exist")
            .contains("dslScheduleRouter");
  }

  @Test
  void getSchedulesReachesHandlerNotFound() throws Exception {
    when(mockService().list()).thenReturn(List.of(
            new ScheduleSummary("sched-A", "A", "0 9 * * *", "UTC", "daily", null, false)));

    mockMvc.perform(get("/api/dsl/schedules"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items[0].scheduleId").value("sched-A"));

    verify(mockService()).list();
  }

  @Test
  void postSchedulesReachesHandlerNotFound() throws Exception {
    when(mockService().create(ArgumentMatchers.any()))
            .thenReturn(new CreateScheduleResponse("sched-A", "A", "0 9 * * *"));

    mockMvc.perform(post("/api/dsl/schedules")
            .contentType("application/json")
            .content("{\"definition\":\"A\",\"cron\":\"0 9 * * *\"}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.scheduleId").value("sched-A"));
  }

  @Test
  void deleteScheduleReachesHandlerNotFound() throws Exception {
    mockMvc.perform(delete("/api/dsl/schedules/A"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.deleted").value(true));

    verify(mockService()).delete("A");
  }

  @Test
  void routerFunctionBeanIsAbsentWithoutScheduleClient() {
    context.close();
    context = new AnnotationConfigApplicationContext();
    context.register(StubBeans.class);
    DslScheduleService service = mock(DslScheduleService.class);
    context.registerBean(DslScheduleService.class, () -> service);
    DslScheduleHandler handler = new DslScheduleHandler(service, new ObjectMapper());
    context.registerBean(DslScheduleHandler.class, () -> handler);
    context.register(DslScheduleRouterConfiguration.class);
    context.refresh();

    assertThat(context.getBeanNamesForType(RouterFunction.class))
            .as("without a ScheduleClient bean, the router config is skipped and no "
                    + "RouterFunction is published (Temporal-gated surface)")
            .doesNotContain("dslScheduleRouter");

    Assertions.assertThatThrownBy(
            () -> context.getBean("dslScheduleRouter", RouterFunction.class))
            .isInstanceOf(NoSuchBeanDefinitionException.class);
  }

  @SuppressWarnings("unchecked")
  private RouterFunction<ServerResponse> routerFunction() {
    return (RouterFunction<ServerResponse>) context.getBean("dslScheduleRouter");
  }

  private DslScheduleService mockService() {
    return context.getBean(DslScheduleService.class);
  }

  private static ExceptionHandlerExceptionResolver exceptionResolver() {
    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();
    return exceptionResolver;
  }

  @Configuration
  static class StubBeans {
    // No beans required — DslScheduleRouterConfiguration is a @Configuration that produces its
    // own RouterFunction bean. ScheduleClient and DslScheduleHandler are registered explicitly in
    // setUp().
  }
}
