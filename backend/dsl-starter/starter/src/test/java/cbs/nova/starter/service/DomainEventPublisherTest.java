package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.DomainEventListener;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Unit tests for {@link DomainEventPublisher}. Pins the frozen JSON shape (field names are a public
 * contract per the design sketch in docs/plans/T411) and the contract that {@code schemaVersion}
 * defaults to {@code 1}, that {@code aggregate_type}/{@code aggregate_id} are extracted from the
 * event, and that publish failures are NOT swallowed at the run path (event loss would defeat the
 * store's purpose).
 */
class DomainEventPublisherTest {

  private final DslEventRepository repository = mock(DslEventRepository.class);
  private final DomainEventPublisher publisher = new DomainEventPublisher(repository,
          new ObjectMapper(), EmptyObjectProvider.of(DomainEventListener.class));

  @Test
  void runStartedSerializesWithFrozenFieldNames() {
    Instant occurredAt = Instant.parse("2026-09-10T12:00:00Z");
    publisher.publish(new DomainEvent.RunStarted(
            "run-123", "MyFlow", "operator-1", occurredAt, "corr-42"));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("RunStarted");
    assertThat(row.aggregateType()).isEqualTo("run");
    assertThat(row.aggregateId()).isEqualTo("run-123");
    assertThat(row.correlationId()).isEqualTo("corr-42");
    assertThat(row.schemaVersion()).isEqualTo(1);
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("eventType").asText()).isEqualTo("RunStarted");
    assertThat(payload.get("runId").asText()).isEqualTo("run-123");
    assertThat(payload.get("processName").asText()).isEqualTo("MyFlow");
    assertThat(payload.get("triggeredBy").asText()).isEqualTo("operator-1");
    assertThat(payload.get("correlationId").asText()).isEqualTo("corr-42");
    assertThat(payload.get("schemaVersion").asInt()).isEqualTo(1);
    assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-09-10T12:00:00Z");
  }

  @Test
  void runCompletedSerializesStatusAndOutputFields() {
    Instant started = Instant.parse("2026-09-10T12:00:00Z");
    Instant finished = Instant.parse("2026-09-10T12:00:05Z");
    publisher.publish(new DomainEvent.RunCompleted(
            "run-1", "MyFlow", DslRunStatus.COMPLETED,
            "{\"result\":42}", null, started, finished,
            finished, "corr-1"));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("RunCompleted");
    assertThat(row.aggregateType()).isEqualTo("run");
    assertThat(row.aggregateId()).isEqualTo("run-1");
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("status").asText()).isEqualTo("COMPLETED");
    assertThat(payload.get("outputJson").asText()).isEqualTo("{\"result\":42}");
    assertThat(payload.get("startedAt").asText()).isEqualTo("2026-09-10T12:00:00Z");
    assertThat(payload.get("finishedAt").asText()).isEqualTo("2026-09-10T12:00:05Z");
    assertThat(payload.get("correlationId").asText()).isEqualTo("corr-1");
  }

  @Test
  void runFailedSerializesErrorField() {
    Instant started = Instant.parse("2026-09-10T12:00:00Z");
    Instant finished = Instant.parse("2026-09-10T12:00:05Z");
    publisher.publish(new DomainEvent.RunFailed(
            "run-2", "MyFlow", DslRunStatus.FAILED,
            "boom", started, finished,
            finished, null));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("RunFailed");
    assertThat(row.aggregateType()).isEqualTo("run");
    assertThat(row.aggregateId()).isEqualTo("run-2");
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("status").asText()).isEqualTo("FAILED");
    assertThat(payload.get("error").asText()).isEqualTo("boom");
    assertThat(payload.get("correlationId").isNull()).isTrue();
  }

  @Test
  void runCancelledSerializesWithCancelFields() {
    Instant started = Instant.parse("2026-09-10T12:00:00Z");
    Instant finished = Instant.parse("2026-09-10T12:00:01Z");
    publisher.publish(new DomainEvent.RunCancelled(
            "run-3", "MyFlow", DslRunStatus.CANCELLED,
            "Cancelled by user", started, finished, finished, null));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("RunCancelled");
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("status").asText()).isEqualTo("CANCELLED");
    assertThat(payload.get("error").asText()).isEqualTo("Cancelled by user");
  }

  @Test
  void runStaleSerializesWithStaleFields() {
    Instant started = Instant.parse("2026-09-10T12:00:00Z");
    Instant finished = Instant.parse("2026-09-10T12:10:00Z");
    publisher.publish(new DomainEvent.RunStale(
            "run-4", "MyFlow", DslRunStatus.STALE,
            "exceeded threshold", started, finished, finished, null));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("RunStale");
    assertThat(parse(row.payloadJson()).get("status").asText()).isEqualTo("STALE");
  }

  @Test
  void draftSavedSerializesWithDefinitionAggregateType() {
    publisher.publish(new DomainEvent.DraftSaved(
            "def-A", "1.0", "default", null, "corr-x"));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("DraftSaved");
    assertThat(row.aggregateType()).isEqualTo("definition");
    assertThat(row.aggregateId()).isEqualTo("def-A");
    assertThat(row.correlationId()).isEqualTo("corr-x");
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("definitionName").asText()).isEqualTo("def-A");
    assertThat(payload.get("version").asText()).isEqualTo("1.0");
    assertThat(payload.get("taskQueue").asText()).isEqualTo("default");
  }

  @Test
  void draftPublishedSerializesReloadedAndLocation() {
    publisher.publish(new DomainEvent.DraftPublished(
            "def-B", "2.0", "default", true, "/dsl/def-B.json",
            null, null));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("DraftPublished");
    assertThat(row.aggregateId()).isEqualTo("def-B");
    JsonNode payload = parse(row.payloadJson());
    assertThat(payload.get("reloaded").asBoolean()).isTrue();
    assertThat(payload.get("location").asText()).isEqualTo("/dsl/def-B.json");
  }

  @Test
  void reloadFailedSerializesWithSourceAsAggregateId() {
    publisher.publish(new DomainEvent.ReloadFailed(
            "def-C", "/dsl", "compilation failed", null, null));

    DslEventEntity row = capturedRow();
    assertThat(row.eventType()).isEqualTo("ReloadFailed");
    assertThat(row.aggregateType()).isEqualTo("definition");
    assertThat(row.aggregateId()).isEqualTo("def-C");
    assertThat(parse(row.payloadJson()).get("error").asText())
            .isEqualTo("compilation failed");
  }

  @Test
  void reloadFailedFallsBackToSourceWhenDefinitionNameAbsent() {
    publisher.publish(new DomainEvent.ReloadFailed(
            null, "/dsl/source", "compilation failed", null, null));

    DslEventEntity row = capturedRow();
    assertThat(row.aggregateId()).isEqualTo("/dsl/source");
  }

  @Test
  void schemaVersionDefaultsToOneForEveryConcreteEvent() {
    publisher.publish(new DomainEvent.RunStarted(
            "r", "p", null, null, null));
    DslEventEntity first = capturedRow();
    assertThat(first.schemaVersion()).isEqualTo(1);
  }

  @Test
  void occurredAtDefaultsToNowWhenAbsent() {
    Instant before = Instant.now();
    publisher.publish(new DomainEvent.RunStarted(
            "r", "p", null, null, null));
    Instant after = Instant.now();

    DslEventEntity row = capturedRow();
    assertThat(row.createdAt()).isBetween(before, after);
  }

  @Test
  void occurredAtTakesEventValueWhenProvided() {
    Instant fixed = Instant.parse("2026-01-01T00:00:00Z");
    publisher.publish(new DomainEvent.RunStarted(
            "r", "p", null, fixed, null));

    DslEventEntity row = capturedRow();
    assertThat(row.createdAt()).isEqualTo(fixed);
  }

  @Test
  void publishFailurePropagatesNotSwallowed() {
    doThrow(new DataAccessResourceFailureException("db gone"))
            .when(repository).insert(any());

    assertThatThrownBy(() -> publisher.publish(new DomainEvent.RunStarted(
            "r", "p", null, null, null)))
            .isInstanceOf(DataAccessResourceFailureException.class);
  }

  private static JsonNode parse(String json) {
    try {
      return JsonMapper.builder().build().readTree(json);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse payload: " + json, e);
    }
  }

  private DslEventEntity capturedRow() {
    ArgumentCaptor<DslEventEntity> captor = ArgumentCaptor.forClass(DslEventEntity.class);
    verify(repository).insert(captor.capture());
    return captor.getValue();
  }
}
