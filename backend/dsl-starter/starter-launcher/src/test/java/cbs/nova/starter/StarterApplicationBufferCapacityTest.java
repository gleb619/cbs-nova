package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class StarterApplicationBufferCapacityTest {

  private Logger applicationLogger;
  private ListAppender<ILoggingEvent> appender;

  @BeforeEach
  void attachAppender() {
    applicationLogger = (Logger) LoggerFactory.getLogger(StarterApplication.class);
    appender = new ListAppender<>();
    appender.start();
    applicationLogger.addAppender(appender);
  }

  @AfterEach
  void detachAppender() {
    applicationLogger.detachAppender(appender);
    System.clearProperty(StarterConstants.STARTUP_BUFFER_CAPACITY_PROPERTY);
  }

  @Test
  void defaultCapacityIsUsedWhenNoConfig() {
    assertThat(StarterApplication.bufferCapacity(null))
            .isEqualTo(StarterConstants.DEFAULT_STARTUP_BUFFER_CAPACITY);
  }

  @Test
  void systemPropertyWinsOverEnv() {
    System.setProperty(StarterConstants.STARTUP_BUFFER_CAPACITY_PROPERTY, "1234");

    assertThat(StarterApplication.bufferCapacity("9999")).isEqualTo(1234);
  }

  @Test
  void envVarIsUsedWhenSystemPropertyIsAbsent() {
    assertThat(StarterApplication.bufferCapacity("777")).isEqualTo(777);
  }

  @Test
  void malformedEnvFallsBackToDefaultWithWarn() {
    assertThat(StarterApplication.bufferCapacity("abc"))
            .isEqualTo(StarterConstants.DEFAULT_STARTUP_BUFFER_CAPACITY);

    assertThat(appender.list).hasSize(1);
    assertThat(appender.list.get(0).getFormattedMessage())
            .containsIgnoringCase("Malformed")
            .contains("abc")
            .contains(String.valueOf(StarterConstants.DEFAULT_STARTUP_BUFFER_CAPACITY));
  }
}
