package cbs.nova.starter.events.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cbs.nova.starter.NotificationTestApplication;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.service.DomainEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T566: real RabbitMQ integration test for the domain-event MQ publisher. Verifies that a consumer
 * on the topic receives events with correct eventType and correlationId.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = NotificationTestApplication.class)
@TestPropertySource(properties = {
    "csb.dsl.worker.enabled=false",
    "spring.flyway.enabled=true",
    "spring.flyway.locations=classpath:db/migration/h2",
    // Dedicated H2 instance: the default shared testdb is already populated by other tests in a
    // full-suite run, which makes Flyway refuse to migrate ("non-empty schema, no history table").
    "spring.datasource.url=jdbc:h2:mem:mq-events-testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE",
    "cbs.nova.events.mq.enabled=true",
    "cbs.nova.events.mq.exchange=cbs.nova.events.exchange",
    "cbs.nova.events.mq.topic=cbs.nova.events"
})
@Testcontainers
class MqDomainEventSinkIntegrationTest {

  private static final String EXCHANGE = "cbs.nova.events.exchange";
  private static final String ROUTING_PATTERN = "cbs.nova.events.*";

  @Container
  static RabbitMQContainer rabbit = new RabbitMQContainer(
          DockerImageName.parse("rabbitmq:3.13-management"));

  @DynamicPropertySource
  static void rabbitProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.rabbitmq.host", rabbit::getHost);
    registry.add("spring.rabbitmq.port", rabbit::getAmqpPort);
  }

  @Autowired
  private DomainEventPublisher publisher;

  @Autowired
  private AmqpAdmin amqpAdmin;

  @Autowired
  private RabbitTemplate rabbitTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  private String queueName;

  @BeforeEach
  void setUp() {
    amqpAdmin.declareExchange(new TopicExchange(EXCHANGE));
    queueName = amqpAdmin
            .declareQueue(new Queue("test-events-" + System.nanoTime(), false, true, true));
    amqpAdmin.declareBinding(BindingBuilder.bind(new Queue(queueName))
            .to(new TopicExchange(EXCHANGE)).with(ROUTING_PATTERN));
  }

  @Test
  void publishesRunStartedEventToTopic() throws Exception {
    DomainEvent event = new DomainEvent.RunStarted(
            "run-mq-1", "MyFlow", "operator-1",
            Instant.parse("2026-09-19T10:00:00Z"), "corr-mq-42");

    publisher.publish(event);

    await().atMost(Duration.ofSeconds(15)).untilAsserted(() -> {
      org.springframework.amqp.core.Message message = rabbitTemplate.receive(queueName, 200);
      assertThat(message).isNotNull();
      assertThat(message.getMessageProperties().getContentType()).isEqualTo("application/json");
      JsonNode envelope = objectMapper.readTree(message.getBody());
      assertThat(envelope.get("eventType").asText()).isEqualTo("RunStarted");
      assertThat(envelope.get("aggregateType").asText()).isEqualTo("run");
      assertThat(envelope.get("aggregateId").asText()).isEqualTo("run-mq-1");
      assertThat(envelope.get("correlationId").asText()).isEqualTo("corr-mq-42");
      assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
      assertThat(envelope.get("dslEventRowId").isNumber()).isTrue();
      assertThat(envelope.get("payload").get("runId").asText()).isEqualTo("run-mq-1");
    });
  }
}
