package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.LinkedBlockingDeque;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Regression coverage for the {@code MDC} path of the dry-run logging pipeline.
 *
 * <p>
 * Mirrors {@link DryRunLogbackAppenderTest} but with {@link MdcDryRunLoggingContext}: the run id is
 * sourced from {@link MDC} rather than from a dedicated {@link ThreadLocal}. This is the setup the
 * Temporal {@code ContextPropagator} relies on at runtime — if the appender ever stops reading from
 * MDC, these tests fail.
 */
class DryRunLogbackAppenderMdcTest {

  private static final int MAX_EVENTS = 100;

  private final DryRunLoggingContext context = new MdcDryRunLoggingContext();
  private final DryRunLogBufferRegistry registry = new DryRunLogBufferRegistry(
          Caffeine.newBuilder().build());
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(context, registry);
  private final Logger logger;

  DryRunLogbackAppenderMdcTest() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    this.logger = context.getLogger(DryRunLogbackAppenderMdcTest.class);
  }

  @BeforeEach
  void setUp() {
    appender.setContext(logger.getLoggerContext());
    appender.start();
    logger.addAppender(appender);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    context.clearRunId();
    MDC.remove(DryRunLoggingContext.RUN_ID_HEADER);
    logger.detachAppender(appender);
    appender.stop();
  }

  private DryRunLogBuffer registerBuffer(String runId) {
    Deque<DryRunLogEvent> queue = new LinkedBlockingDeque<>(MAX_EVENTS);
    DryRunLogBuffer buffer = new DryRunLogBuffer(MAX_EVENTS, queue);
    registry.register(runId, buffer);
    return buffer;
  }

  @Test
  void appenderReadsRunIdFromContextImpl() {
    String runId = "run-mdc-1";
    DryRunLogBuffer buffer = registerBuffer(runId);

    context.runWithRunId(runId, () -> logger.info("via context"));

    List<DryRunLogEvent> events = buffer.drain();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().message()).isEqualTo("via context");
    assertThat(events.getFirst().runId()).isEqualTo(runId);
  }

  @Test
  void appenderReadsRunIdDirectlyFromMdc() {
    // Models what DryRunLoggingContextPropagator does at runtime: MDC is the source of truth.
    String runId = "run-mdc-2";
    DryRunLogBuffer buffer = registerBuffer(runId);

    MDC.put(DryRunLoggingContext.RUN_ID_HEADER, runId);
    try {
      logger.info("via mdc");
    } finally {
      MDC.remove(DryRunLoggingContext.RUN_ID_HEADER);
    }

    List<DryRunLogEvent> events = buffer.drain();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().message()).isEqualTo("via mdc");
    assertThat(events.getFirst().runId()).isEqualTo(runId);
  }

  @Test
  void appenderDropsEventsAfterMdcCleared() {
    String runId = "run-mdc-3";
    DryRunLogBuffer buffer = registerBuffer(runId);

    context.setRunId(runId);
    logger.info("captured");
    context.clearRunId();
    logger.info("dropped");

    List<DryRunLogEvent> events = buffer.drain();
    assertThat(events).hasSize(1);
    assertThat(events.getFirst().message()).isEqualTo("captured");
  }

  @Test
  void twoRunsAreIsolatedThroughMdc() {
    String runIdA = "run-mdc-a";
    String runIdB = "run-mdc-b";
    DryRunLogBuffer bufferA = registerBuffer(runIdA);
    DryRunLogBuffer bufferB = registerBuffer(runIdB);

    context.runWithRunId(runIdA, () -> logger.info("event-a"));
    context.runWithRunId(runIdB, () -> logger.info("event-b"));

    List<DryRunLogEvent> eventsA = bufferA.drain();
    List<DryRunLogEvent> eventsB = bufferB.drain();

    assertThat(eventsA).hasSize(1);
    assertThat(eventsA.getFirst().message()).isEqualTo("event-a");
    assertThat(eventsA.getFirst().runId()).isEqualTo(runIdA);
    assertThat(eventsB).hasSize(1);
    assertThat(eventsB.getFirst().message()).isEqualTo("event-b");
    assertThat(eventsB.getFirst().runId()).isEqualTo(runIdB);
  }
}
