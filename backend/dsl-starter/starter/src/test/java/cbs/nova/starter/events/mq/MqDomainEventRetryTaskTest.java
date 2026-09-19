package cbs.nova.starter.events.mq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.DomainEvent;
import cbs.nova.starter.events.sink.MqDomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * T566: unit tests for the MQ retry worker. Proves that unpublished rows are replayed and that a
 * failing sink is retried until it succeeds.
 */
class MqDomainEventRetryTaskTest {

  private final DslEventRepository repository = mock(DslEventRepository.class);
  private final MqDomainEventSink sink = mock(MqDomainEventSink.class);
  private final MqEventProperties properties = new MqEventProperties(
          true, "ex", "topic", "rk", 100, 30);
  private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
  private final Clock clock = Clock.fixed(Instant.parse("2026-09-19T12:00:00Z"), ZoneId.of("UTC"));

  private final MqDomainEventRetryTask task = new MqDomainEventRetryTask(
          repository, sink, properties, executor, clock);

  @Test
  void replaysUnpublishedRowsAndMarksPublished() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    DomainEvent event = new DomainEvent.RunStarted(
            "run-1", "Flow", "operator", Instant.now(), "corr");
    String payload = objectMapper.writeValueAsString(event);
    DslEventEntity row = new DslEventEntity(42L, "RunStarted", "run", "run-1",
            "corr", payload, 1, clock.instant());

    when(repository.findUnpublished(anyInt())).thenReturn(List.of(row));

    task.run();

    verify(sink).onEvent(row);
  }

  @Test
  void retriesWhenSinkFailsFirstTime() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    DomainEvent event = new DomainEvent.RunStarted(
            "run-2", "Flow", "operator", Instant.now(), "corr");
    String payload = objectMapper.writeValueAsString(event);
    DslEventEntity row = new DslEventEntity(99L, "RunStarted", "run", "run-2",
            "corr", payload, 1, clock.instant());

    when(repository.findUnpublished(anyInt()))
            .thenReturn(List.of(row))
            .thenReturn(List.of(row))
            .thenReturn(List.of());
    doThrow(new RuntimeException("broker down"))
            .doNothing()
            .when(sink).onEvent(any(DslEventEntity.class));

    task.run();
    task.run();

    verify(sink, times(2)).onEvent(row);
  }
}
