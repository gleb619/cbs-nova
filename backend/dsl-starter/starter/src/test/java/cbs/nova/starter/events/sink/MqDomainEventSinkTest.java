package cbs.nova.starter.events.sink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.mq.DomainEventMqEnvelope;
import cbs.nova.starter.persistence.DslEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * T808: Docker-free unit spec for {@link MqDomainEventSink}. Exercises every branch the production
 * code can take without standing up a real broker or Spring context. {@link AmqpTemplate} and
 * {@link DslEventRepository} are mocked; a real Jackson {@link ObjectMapper} is reused so the JSON
 * envelope assertions reflect production serialization exactly.
 */
class MqDomainEventSinkTest {

  private AmqpTemplate amqpTemplate;
  private DslEventRepository repository;
  private ObjectMapper objectMapper;
  private MqEventProperties properties;

  @BeforeEach
  void setUp() {
    amqpTemplate = mock(AmqpTemplate.class);
    repository = mock(DslEventRepository.class);
    objectMapper = new ObjectMapper();
    // enabled=true, exchange="cbs.nova.events.exchange", topic="cbs.nova.events", routingKey=null,
    // retry knobs are unused here but must be > 0 per the record's compact ctor.
    properties = new MqEventProperties(true, "cbs.nova.events.exchange",
            "cbs.nova.events", null, 100, 30);
  }

  private MqDomainEventSink sink() {
    return new MqDomainEventSink(amqpTemplate, objectMapper, repository, properties);
  }

  // --- properties.enabled() = false -------------------------------------------------

  @Test
  void disabledPropertySkipsAmqpAndRepositoryForEventOverload() {
    properties = new MqEventProperties(false, "cbs.nova.events.exchange",
            "cbs.nova.events", null, 100, 30);

    sink().onEvent(sampleRunStarted(), 7L);

    verifyNoInteractions(amqpTemplate, repository);
  }

  @Test
  void disabledPropertySkipsAmqpAndRepositoryForRowOverload() {
    properties = new MqEventProperties(false, "cbs.nova.events.exchange",
            "cbs.nova.events", null, 100, 30);

    sink().onEvent(sampleRow(42L));

    verifyNoInteractions(amqpTemplate, repository);
  }

  // --- Blank/null exchange ----------------------------------------------------------

  @Test
  void blankExchangeDropsEventAndDoesNotMarkPublished() {
    properties = new MqEventProperties(true, "  ", "cbs.nova.events", null, 100, 30);

    sink().onEvent(sampleRunStarted(), 11L);

    verifyNoInteractions(amqpTemplate, repository);
  }

  @Test
  void nullExchangeDropsEventAndDoesNotMarkPublished() {
    properties = new MqEventProperties(true, null, "cbs.nova.events", null, 100, 30);

    sink().onEvent(sampleRunStarted(), 12L);

    verifyNoInteractions(amqpTemplate, repository);
  }

  // --- Happy path -------------------------------------------------------------------

  @Test
  void happyPathUsesEffectiveRoutingKeyAndDeserializableEnvelope() throws Exception {
    DomainEvent event = sampleRunStarted();
    long rowId = 99L;

    sink().onEvent(event, rowId);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(amqpTemplate, times(1))
            .send(eq("cbs.nova.events.exchange"), eq("cbs.nova.events.RunStarted"),
                    captor.capture());
    verify(repository, times(1)).markPublished(rowId);

    Message sent = captor.getValue();
    assertThat(sent.getMessageProperties().getContentType())
            .isEqualTo(MessageProperties.CONTENT_TYPE_JSON);
    assertThat(sent.getMessageProperties().getHeaders())
            .containsEntry("eventType", "RunStarted")
            .containsEntry("aggregateType", "run")
            .containsEntry("aggregateId", "run-mq-1")
            .containsEntry("correlationId", "corr-mq-42");

    DomainEventMqEnvelope envelope = objectMapper.readValue(
            sent.getBody(), DomainEventMqEnvelope.class);
    assertThat(envelope.eventType()).isEqualTo("RunStarted");
    assertThat(envelope.aggregateType()).isEqualTo("run");
    assertThat(envelope.aggregateId()).isEqualTo("run-mq-1");
    assertThat(envelope.correlationId()).isEqualTo("corr-mq-42");
    assertThat(envelope.schemaVersion()).isEqualTo(1);
    assertThat(envelope.dslEventRowId()).isEqualTo(rowId);
    assertThat(envelope.occurredAt()).isEqualTo(Instant.parse("2026-09-19T10:00:00Z"));
    JsonNode payload = envelope.payload();
    assertThat(payload.get("runId").asText()).isEqualTo("run-mq-1");
    assertThat(payload.get("processName").asText()).isEqualTo("MyFlow");
    assertThat(payload.get("triggeredBy").asText()).isEqualTo("operator-1");
    assertThat(new String(sent.getBody(), StandardCharsets.UTF_8))
            .contains("\"dslEventRowId\":" + rowId);
  }

  @Test
  void explicitRoutingKeyOverridesTopicPattern() {
    properties = new MqEventProperties(true, "cbs.nova.events.exchange",
            "cbs.nova.events", "custom.rk", 100, 30);

    sink().onEvent(sampleRunStarted(), 1L);

    verify(amqpTemplate).send(eq("cbs.nova.events.exchange"), eq("custom.rk"), any(Message.class));
    verify(repository).markPublished(1L);
  }

  // --- amqpTemplate.send throws ------------------------------------------------------

  @Test
  void amqpSendFailureIsSwallowedAndRowStaysRetryable() {
    org.mockito.Mockito.doThrow(new AmqpException("broker down"))
            .when(amqpTemplate).send(any(String.class), any(String.class), any(Message.class));

    sink().onEvent(sampleRunStarted(), 23L);

    verify(repository, never()).markPublished(anyLong());
  }

  // --- Null correlationId -----------------------------------------------------------

  @Test
  void nullCorrelationIdOnEventOverloadDoesNotThrow() {
    DomainEvent event = new DomainEvent.RunStarted(
            "run-x", "MyFlow", "operator-1",
            Instant.parse("2026-09-19T10:00:00Z"), null);

    assertThatCode(() -> sink().onEvent(event, 5L)).doesNotThrowAnyException();

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(amqpTemplate).send(any(String.class), any(String.class), captor.capture());
    // Pin actual behavior: setHeaderIfAbsent stores the null in the headers map (no NPE).
    assertThat(captor.getValue().getMessageProperties().getHeaders())
            .containsKey("correlationId");
    verify(repository).markPublished(5L);
  }

  @Test
  void nullCorrelationIdOnRowOverloadDoesNotThrow() {
    DslEventEntity row = new DslEventEntity(
            6L, "RunStarted", "run", "run-y", null,
            "{\"runId\":\"run-y\",\"processName\":\"MyFlow\",\"triggeredBy\":\"op\","
                    + "\"occurredAt\":\"2026-09-19T10:00:00Z\"}",
            1, Instant.parse("2026-09-19T10:00:00Z"));

    assertThatCode(() -> sink().onEvent(row)).doesNotThrowAnyException();

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(amqpTemplate).send(any(String.class), any(String.class), captor.capture());
    assertThat(captor.getValue().getMessageProperties().getHeaders())
            .containsKey("correlationId");
    verify(repository).markPublished(6L);
  }

  // --- Row-overload specifics -------------------------------------------------------

  @Test
  void rowOverloadUsesCreatedAtAsOccurredAt() throws Exception {
    DslEventEntity row = sampleRow(77L);

    sink().onEvent(row);

    ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
    verify(amqpTemplate).send(any(String.class), any(String.class), captor.capture());
    verify(repository).markPublished(77L);

    DomainEventMqEnvelope envelope = objectMapper.readValue(
            captor.getValue().getBody(), DomainEventMqEnvelope.class);
    assertThat(envelope.occurredAt()).isEqualTo(row.createdAt());
    assertThat(envelope.dslEventRowId()).isEqualTo(77L);
  }

  @Test
  void rowOverloadWithInvalidPayloadJsonIsSwallowed() {
    DslEventEntity row = new DslEventEntity(
            8L, "RunStarted", "run", "run-z", "corr",
            "not-json-{", 1, Instant.parse("2026-09-19T10:00:00Z"));

    assertThatCode(() -> sink().onEvent(row)).doesNotThrowAnyException();

    verifyNoInteractions(amqpTemplate);
    verify(repository, never()).markPublished(anyLong());
  }

  // --- helpers ----------------------------------------------------------------------

  private static DomainEvent sampleRunStarted() {
    return new DomainEvent.RunStarted(
            "run-mq-1", "MyFlow", "operator-1",
            Instant.parse("2026-09-19T10:00:00Z"), "corr-mq-42");
  }

  private static DslEventEntity sampleRow(long id) {
    String payload = "{\"runId\":\"run-1\",\"processName\":\"Flow\",\"triggeredBy\":\"operator\","
            + "\"occurredAt\":\"2026-09-19T10:00:00Z\"}";
    return new DslEventEntity(
            id, "RunStarted", "run", "run-1", "corr-1",
            payload, 1, Instant.parse("2026-09-19T10:00:00Z"));
  }
}
