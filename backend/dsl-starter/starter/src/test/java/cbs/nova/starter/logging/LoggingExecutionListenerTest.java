package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.web.RequestIdFilter;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.List;
import java.util.Map;

class LoggingExecutionListenerTest {

  private final Logger logger = (Logger) LoggerFactory.getLogger(LoggingExecutionListener.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
  private LoggingExecutionListener listener;

  @BeforeEach
  void setUp() {
    appender.start();
    logger.addAppender(appender);
    listener = new LoggingExecutionListener(new CbsNovaLoggingProperties(
            CbsNovaLoggingProperties.Level.DEBUG,
            CbsNovaLoggingProperties.Level.INFO,
            true));
    MDC.clear();
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
    MDC.clear();
  }

  @Test
  void processStartLogsWithRunIdInMdc() {
    listener.onProcessStart("run-1", "MyProcess", Map.of("key", "value"));

    List<ILoggingEvent> events = appender.list;
    assertThat(events).hasSize(1);
    assertThat(events.get(0).getFormattedMessage()).contains("MyProcess");
    assertThat(events.get(0).getMDCPropertyMap().get(RequestIdFilter.REQUEST_ID_MDC_KEY))
            .isEqualTo("run-1");
  }

  @Test
  void processFailureLogsAtError() {
    listener.onProcessEnd("run-2", "MyProcess", null, false);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getLevel().toString()).isEqualTo("ERROR");
  }

  @Test
  void respectsConfiguredLifecycleLevel() {
    listener = new LoggingExecutionListener(new CbsNovaLoggingProperties(
            CbsNovaLoggingProperties.Level.ERROR,
            CbsNovaLoggingProperties.Level.INFO,
            true));

    listener.onTransactionStart("run-3", "MyTx", null);

    assertThat(appender.list).isEmpty();
  }
}
