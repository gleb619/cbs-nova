package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;

class DryRunLogbackAppenderTest {

  private final DryRunLoggingContext context = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(context, 100);
  private final Logger logger;

  DryRunLogbackAppenderTest() {
    LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
    this.logger = context.getLogger(DryRunLogbackAppenderTest.class);
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
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void logStatementInsideDryRunContextIsCaptured() {
    String runId = "run-1";
    context.runWithRunId(runId, () -> logger.info("hello dry run"));

    List<DryRunLogEvent> events = appender.drain(runId);
    assertThat(events).hasSize(1);
    DryRunLogEvent event = events.getFirst();
    assertThat(event.level()).isEqualTo("INFO");
    assertThat(event.message()).isEqualTo("hello dry run");
    assertThat(event.runId()).isEqualTo(runId);
    assertThat(event.timestampMillis()).isPositive();
  }

  @Test
  void logStatementOutsideDryRunContextIsNotCaptured() {
    String runId = "run-2";

    logger.info("normal log");

    List<DryRunLogEvent> events = appender.drain(runId);
    assertThat(events).isEmpty();
  }

  @Test
  void drainReturnsCapturedEventsAndClearsBuffer() {
    String runId = "run-3";
    context.runWithRunId(runId, () -> {
      logger.info("first");
      logger.info("second");
    });

    List<DryRunLogEvent> firstDrain = appender.drain(runId);
    List<DryRunLogEvent> secondDrain = appender.drain(runId);

    assertThat(firstDrain).hasSize(2);
    assertThat(secondDrain).isEmpty();
  }

  @Test
  void eventsFromDifferentRunIdsAreIsolated() {
    String runIdA = "run-a";
    String runIdB = "run-b";

    context.setRunId(runIdA);
    logger.info("event-a");
    context.clearRunId();

    context.setRunId(runIdB);
    logger.info("event-b");
    context.clearRunId();

    List<DryRunLogEvent> eventsA = appender.drain(runIdA);
    List<DryRunLogEvent> eventsB = appender.drain(runIdB);

    assertThat(eventsA).hasSize(1);
    assertThat(eventsA.getFirst().message()).isEqualTo("event-a");
    assertThat(eventsA.getFirst().runId()).isEqualTo(runIdA);
    assertThat(eventsB).hasSize(1);
    assertThat(eventsB.getFirst().message()).isEqualTo("event-b");
    assertThat(eventsB.getFirst().runId()).isEqualTo(runIdB);
  }

  @Test
  void bufferDropsOldestEventsWhenMaxIsReached() {
    String runId = "run-full";
    context.setRunId(runId);
    for (int i = 0; i < 105; i++) {
      logger.info("event-{}", i);
    }
    context.clearRunId();

    List<DryRunLogEvent> events = appender.drain(runId);

    assertThat(events).hasSize(100);
    assertThat(events.getFirst().message()).isEqualTo("event-5");
    assertThat(events.getLast().message()).isEqualTo("event-104");
  }
}
