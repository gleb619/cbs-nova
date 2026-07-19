package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

class DryRunLogbackAppenderTest {

  private final DryRunLogbackAppender appender = new DryRunLogbackAppender();
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
    DryRunLoggingContext.leaveDryRun();
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  void logStatementInsideDryRunContextIsCaptured() {
    String runId = "run-1";
    DryRunLoggingContext.enterDryRun(runId);

    logger.info("hello dry run");

    List<Map<String, Object>> events = appender.drain(runId);
    assertThat(events).hasSize(1);
    assertThat(events.getFirst())
            .containsEntry("level", "INFO")
            .containsEntry("message", "hello dry run")
            .containsEntry("logger", logger.getName())
            .containsKey("timestamp");
  }

  @Test
  void logStatementOutsideDryRunContextIsNotCaptured() {
    String runId = "run-2";

    logger.info("normal log");

    List<Map<String, Object>> events = appender.drain(runId);
    assertThat(events).isEmpty();
  }

  @Test
  void drainReturnsCapturedEventsAndClearsBuffer() {
    String runId = "run-3";
    DryRunLoggingContext.enterDryRun(runId);

    logger.info("first");
    logger.info("second");

    List<Map<String, Object>> firstDrain = appender.drain(runId);
    List<Map<String, Object>> secondDrain = appender.drain(runId);

    assertThat(firstDrain).hasSize(2);
    assertThat(secondDrain).isEmpty();
  }

  @Test
  void eventsFromDifferentRunIdsAreIsolated() {
    String runIdA = "run-a";
    String runIdB = "run-b";

    DryRunLoggingContext.enterDryRun(runIdA);
    logger.info("event-a");
    DryRunLoggingContext.leaveDryRun();

    DryRunLoggingContext.enterDryRun(runIdB);
    logger.info("event-b");
    DryRunLoggingContext.leaveDryRun();

    List<Map<String, Object>> eventsA = appender.drain(runIdA);
    List<Map<String, Object>> eventsB = appender.drain(runIdB);

    assertThat(eventsA).hasSize(1);
    assertThat(eventsA.getFirst()).containsEntry("message", "event-a");
    assertThat(eventsB).hasSize(1);
    assertThat(eventsB.getFirst()).containsEntry("message", "event-b");
  }
}
