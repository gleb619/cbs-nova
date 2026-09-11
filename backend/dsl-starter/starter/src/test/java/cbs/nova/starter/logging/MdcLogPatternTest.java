package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

/**
 * Verifies the logback pattern syntax used by {@code logging.pattern.level} in application.yml
 * (keep in sync): both MDC keys render with their values when set and as empty strings — not
 * {@code null} — when a request is uncorrelated.
 */
class MdcLogPatternTest {

  private static final String LOG_LEVEL_PATTERN = "%5p [rid=%X{rid:-} cid=%X{cid:-}]";

  private final LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void correlatedRequestRendersBothMdcKeys() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(loggerContext);
    appender.start();
    PatternLayout layout = newPatternLayout();
    Logger logger = loggerContext
            .getLogger(MdcLogPatternTest.class.getName());
    logger.addAppender(appender);
    try {
      MDC.put("rid", "req-abc");
      MDC.put("cid", "order-4711");
      logger.info("correlated request");

      assertThat(layout.doLayout(appender.list.get(0)))
              .contains("INFO [rid=req-abc cid=order-4711]");
    } finally {
      logger.detachAppender(appender);
      layout.stop();
      appender.stop();
    }
  }

  @Test
  void uncorrelatedRequestRendersEmptyValuesNotNull() {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.setContext(loggerContext);
    appender.start();
    PatternLayout layout = newPatternLayout();
    Logger logger = loggerContext
            .getLogger(MdcLogPatternTest.class.getName());
    logger.addAppender(appender);
    try {
      logger.info("uncorrelated request");

      assertThat(layout.doLayout(appender.list.get(0)))
              .contains("INFO [rid= cid=]")
              .doesNotContain("null");
    } finally {
      logger.detachAppender(appender);
      layout.stop();
      appender.stop();
    }
  }

  private PatternLayout newPatternLayout() {
    PatternLayout layout = new PatternLayout();
    layout.setContext(loggerContext);
    layout.setPattern(LOG_LEVEL_PATTERN);
    layout.start();
    return layout;
  }
}
