package cbs.nova.starter.reporting;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import cbs.nova.starter.config.properties.CbsStartupReportProperties;
import java.time.Duration;
import java.util.Collection;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.metrics.buffering.BufferingApplicationStartup;
import org.springframework.context.support.GenericApplicationContext;

class StartupTimeReporterTest {

  CbsStartupReportProperties cbsStartupReportProperties = new CbsStartupReportProperties(true, 10, 100);

  private StartupTimeReporter startupTimeReporter;

  @BeforeEach
  void setUp() {
    startupTimeReporter = new StartupTimeReporter(cbsStartupReportProperties);
  }

  @Test
  void aggregatesStepsByNameAndListsSlowSteps() {
    var startup = new BufferingApplicationStartup(100);
    var first = startup.start("beans.instantiate");
    busyWait(60);
    first.end();
    var second = startup.start("beans.instantiate");
    busyWait(60);
    second.end();
    var other = startup.start("context.refresh");
    busyWait(20);
    other.end();

    var lines = startupTimeReporter.summarize(
            startup.drainBufferedTimeline(), 10, Duration.ofMillis(50))
        .stream()
        .flatMap(Collection::stream)
        .collect(Collectors.toList());;

    assertThat(lines.get(0)).startsWith("step=beans.instantiate totalMillis=");
    assertThat(lines).anyMatch(line -> line.startsWith("step=context.refresh totalMillis="));
    assertThat(lines).filteredOn(line -> line.startsWith("slow=beans.instantiate")).hasSize(2);
    assertThat(lines).noneMatch(line -> line.startsWith("slow=context.refresh"));
  }

  @Test
  void topStepsLimitIsApplied() {
    var startup = new BufferingApplicationStartup(100);
    for (int i = 0; i < 5; i++) {
      startup.start("step-" + i).end();
    }

    var lines = startupTimeReporter.summarize(
            startup.drainBufferedTimeline(), 2, Duration.ofDays(1))
        .stream()
        .flatMap(Collection::stream)
        .toList();

    assertThat(lines.stream().filter(line -> line.startsWith("step=")).count()).isEqualTo(2);
  }

  @Test
  void reportLogsStartupSummaryOnApplicationReady() {
    var startup = new BufferingApplicationStartup(100);
    var step = startup.start("beans.instantiate");
    busyWait(60);
    step.end();
    var context = new GenericApplicationContext();
    context.setApplicationStartup(startup);
    context.refresh();
    var appender = new ListAppender<ILoggingEvent>();
    Logger logger = (Logger) LoggerFactory.getLogger(StartupTimeReporter.class);
    logger.addAppender(appender);
    appender.start();
    var event = new ApplicationReadyEvent(
            new SpringApplication(), new String[0], context, Duration.ofMillis(4321));
    try {
      new StartupTimeReporter(new CbsStartupReportProperties(true, 10, 50)).report(event);
    } finally {
      logger.detachAppender(appender);
      context.close();
    }

    assertThat(appender.list)
            .anyMatch(e -> e.getFormattedMessage().contains("STARTUP_REPORT totalMillis=4321"))
            .anyMatch(e -> e.getFormattedMessage().contains("slow=beans.instantiate"))
            .allMatch(e -> e.getLevel() == Level.INFO);
  }

  @Test
  void reportIsSkippedWhenDisabled() {
    var context = new GenericApplicationContext();
    context.refresh();
    var appender = new ListAppender<ILoggingEvent>();
    Logger logger = (Logger) LoggerFactory.getLogger(StartupTimeReporter.class);
    logger.addAppender(appender);
    appender.start();
    var event = new ApplicationReadyEvent(
            new SpringApplication(), new String[0], context, Duration.ofMillis(1));
    try {
      new StartupTimeReporter(new CbsStartupReportProperties(false, 10, 50)).report(event);
    } finally {
      logger.detachAppender(appender);
      context.close();
    }

    assertThat(appender.list).isEmpty();
  }

  private static void busyWait(long millis) {
    long deadline = System.nanoTime() + millis * 1_000_000;
    while (System.nanoTime() < deadline) {
      Thread.onSpinWait();
    }
  }
}
