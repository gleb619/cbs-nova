package cbs.nova.starter.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cbs.nova.starter.config.router.WebhookRouterConfiguration;
import cbs.nova.starter.converter.DefaultDslExceptionMapper;
import cbs.nova.starter.webhook.WebhookDeliveryRecord;
import cbs.nova.starter.webhook.WebhookDeliveryRecordRepository;
import cbs.nova.starter.webhook.WebhookDeliveryTestSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.mvc.method.annotation.ExceptionHandlerExceptionResolver;

/**
 * Read-side tests for {@code GET /api/dsl/webhooks/deliveries}: {@code PageResponse} envelope,
 * pagination clamping, and the {@code subscriptionId} filter.
 */
class WebhookHandlerTest {

  private WebhookDeliveryRecordRepository repository;
  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    repository = WebhookDeliveryTestSupport.h2();
    WebhookHandler handler = new WebhookHandler(Optional.empty(), repository);
    WebhookRouterConfiguration router = new WebhookRouterConfiguration();

    AnnotationConfigApplicationContext adviceContext = new AnnotationConfigApplicationContext();
    adviceContext.registerBean(DslExceptionHandler.class,
            () -> new DslExceptionHandler(new DefaultDslExceptionMapper()));
    adviceContext.refresh();

    ExceptionHandlerExceptionResolver exceptionResolver = new ExceptionHandlerExceptionResolver();
    exceptionResolver.setApplicationContext(adviceContext);
    exceptionResolver.setMessageConverters(List.of(new JacksonJsonHttpMessageConverter()));
    exceptionResolver.afterPropertiesSet();

    mockMvc = MockMvcBuilders.routerFunctions(router.webhookRouter(handler))
            .setMessageConverters(new StringHttpMessageConverter(),
                    new JacksonJsonHttpMessageConverter())
            .setHandlerExceptionResolvers(exceptionResolver)
            .build();
  }

  @Test
  void emptyTableReturnsEmptyEnvelope() throws Exception {
    mockMvc.perform(get("/api/dsl/webhooks/deliveries"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.items").isArray())
            .andExpect(jsonPath("$.items").isEmpty())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(50));
  }

  @Test
  void listReturnsNewestFirstWithEnvelope() throws Exception {
    seed(5);

    mockMvc.perform(get("/api/dsl/webhooks/deliveries").param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(0))
            .andExpect(jsonPath("$.limit").value(2))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].subscriptionId").value("sub-4"))
            .andExpect(jsonPath("$.items[1].subscriptionId").value("sub-3"))
            .andExpect(jsonPath("$.items[0].eventType").value("run.completed"))
            .andExpect(jsonPath("$.items[0].status").value("200"))
            .andExpect(jsonPath("$.items[0].attempts").value(1))
            .andExpect(jsonPath("$.items[0].occurredAt").exists());
  }

  @Test
  void offsetSkipsRows() throws Exception {
    seed(5);

    mockMvc.perform(get("/api/dsl/webhooks/deliveries").param("limit", "2").param("offset", "3"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(5))
            .andExpect(jsonPath("$.offset").value(3))
            .andExpect(jsonPath("$.items", hasSize(2)))
            .andExpect(jsonPath("$.items[0].subscriptionId").value("sub-1"))
            .andExpect(jsonPath("$.items[1].subscriptionId").value("sub-0"));
  }

  @Test
  void subscriptionIdFilterNarrowsResults() throws Exception {
    Instant now = Instant.now();
    repository.insert(new WebhookDeliveryRecord(null, now.minus(1, ChronoUnit.MINUTES),
            "sub-a", "run.completed", "https://example.com/a", "200", 1, null, 10L));
    repository.insert(new WebhookDeliveryRecord(null, now, "sub-b", "run.completed",
            "https://example.com/b", "500", 2, "boom", 20L));

    mockMvc.perform(get("/api/dsl/webhooks/deliveries").param("subscriptionId", "sub-b"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(1))
            .andExpect(jsonPath("$.items", hasSize(1)))
            .andExpect(jsonPath("$.items[0].subscriptionId").value("sub-b"))
            .andExpect(jsonPath("$.items[0].status").value("500"))
            .andExpect(jsonPath("$.items[0].lastError").value("boom"))
            .andExpect(jsonPath("$.items[0].durationMs").value(20));
  }

  @Test
  void limitIsClampedToMax() throws Exception {
    seed(3);

    mockMvc.perform(get("/api/dsl/webhooks/deliveries").param("limit", "99999"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.limit").value(500))
            .andExpect(jsonPath("$.items", hasSize(3)));
  }

  @Test
  void nonNumericLimitReturns400() throws Exception {
    mockMvc.perform(get("/api/dsl/webhooks/deliveries").param("limit", "abc"))
            .andExpect(status().isBadRequest());
  }

  private void seed(int count) {
    Instant now = Instant.now();
    for (int i = 0; i < count; i++) {
      repository.insert(new WebhookDeliveryRecord(null,
              now.minus(count - i, ChronoUnit.MINUTES), "sub-" + i, "run.completed",
              "https://example.com/" + i, "200", 1, null, 100L));
    }
  }
}
