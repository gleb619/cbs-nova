package cbs.nova.starter.events.mq;

import cbs.nova.starter.config.properties.MqEventProperties;
import cbs.nova.starter.entity.DslEventEntity;
import cbs.nova.starter.events.sink.MqDomainEventSink;
import cbs.nova.starter.persistence.DslEventRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Background retry worker for the MQ sink. Replays rows where {@code dsl_events.mq_published} is
 * still {@code false}; once the sink accepts the event the row is marked published.
 */
@Slf4j
@RequiredArgsConstructor
public class MqDomainEventRetryTask {

  private final DslEventRepository repository;
  private final MqDomainEventSink sink;
  private final MqEventProperties properties;
  private final ScheduledExecutorService executor;
  private final Clock clock;

  public void start() {
    long intervalSeconds = properties.retryIntervalSeconds();
    executor.scheduleAtFixedRate(this::run, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
    log.info("[DSL events MQ] retry task started with interval={}s", intervalSeconds);
  }

  void run() {
    try {
      List<DslEventEntity> rows = repository.findUnpublished(properties.retryBatchSize());
      if (rows.isEmpty()) {
        return;
      }
      log.debug("[DSL events MQ] retrying {} unpublished event rows", rows.size());
      for (DslEventEntity row : rows) {
        retry(row);
      }
    } catch (Exception e) {
      log.warn("[DSL events MQ] retry run failed: {}", e.getMessage());
    }
  }

  private void retry(DslEventEntity row) {
    try {
      sink.onEvent(row);
    } catch (Exception e) {
      log.warn("[DSL events MQ] retry failed for row {}: {}", row.id(), e.getMessage());
    }
  }

  /**
   * Returns the instant before which rows are considered safe to retry. A small grace window avoids
   * racing with the initial inline publish.
   */
  public Instant retryCutoff() {
    return clock.instant().minus(Duration.ofSeconds(10));
  }
}
