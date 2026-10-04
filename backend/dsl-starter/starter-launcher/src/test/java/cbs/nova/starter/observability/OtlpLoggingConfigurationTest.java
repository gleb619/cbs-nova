package cbs.nova.starter.observability;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.read.ListAppender;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import java.lang.reflect.Field;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.env.MockEnvironment;

/**
 * T842 — default-off contract for the OTLP log appender installer.When no OTLP endpoint is
 * configured the installer must leave the OpenTelemetryAppender on the root logger without an SDK,
 * so events buffer but no network call happens. With an endpoint configured the installer must wire
 * an SDK and call {@link OpenTelemetryAppender#install(io.opentelemetry.api.OpenTelemetry)} so the
 * buffer drains.
 */
class OtlpLoggingConfigurationTest {

  @AfterEach
  void resetOtelAppender() throws Exception {
    detachFromRoot();
    forceNullOpenTelemetry();
  }

  @Test
  void defaultOffLeavesOpenTelemetryUnset() throws Exception {
    detachFromRoot();
    var appender = new OpenTelemetryAppender();
    appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);

    var config = new OtlpLoggingConfiguration(new MockEnvironment());
    config.installIfEnabled();

    assertThat(readOpenTelemetry(appender)).as("default-offSDK").isNull();
  }

  @Test
  void envVarActivatesInstaller() throws Exception {
    detachFromRoot();
    var appender = new OpenTelemetryAppender();
    appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();
    ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(appender);

    var env = new MockEnvironment();
    env.setProperty("otel.exporter.otlp.endpoint", "http://localhost:4318/v1/logs");
    var config = new OtlpLoggingConfiguration(env);

    try {
      config.installIfEnabled();
      assertThat(readOpenTelemetry(appender))
              .as("OTLP endpoint set → SDK installed on appender")
              .isNotNull();
    } finally {
      forceNullOpenTelemetry();
    }
  }
  void xtest123() throws Exception {
    java.net.URL xml = getClass().getClassLoader().getResource("logback-spring.xml");
    assertThat(xml).isNotNull();
    String content = new String(xml.openStream().readAllBytes(),
            java.nio.charset.StandardCharsets.UTF_8);
    assertThat(content)
            .as("must declare OpenTelemetryAppender")
            .contains("<appender name=\"OpenTelemetry\"")
            .contains("captureMdcAttributes")
            .contains("rid,cid");
  }
  @Test
  void openTelemetryAppenderBuffersWhenNoSdkInstalled() throws Exception {
    var appender = new OpenTelemetryAppender();
    appender.setContext((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory());
    appender.start();

    var logger = (Logger) LoggerFactory.getLogger(OtlpLoggingConfigurationTest.class);
    logger.addAppender(appender);
    try {
      logger.info("buffered-while-inert");
      assertThat(readOpenTelemetry(appender)).isNull();
    } finally {
      logger.detachAppender(appender);
      appender.stop();
    }
  }

  private static void detachFromRoot() {
    var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    var toDetach = new java.util.ArrayList<Appender<ILoggingEvent>>();
    root.iteratorForAppenders().forEachRemaining(a -> toDetach.add((Appender<ILoggingEvent>) a));
    toDetach.stream()
            .filter(a -> a instanceof OpenTelemetryAppender)
            .forEach(root::detachAppender);
  }

  private static io.opentelemetry.api.OpenTelemetry readOpenTelemetry(
          OpenTelemetryAppender appender)
          throws Exception {
    Field f = OpenTelemetryAppender.class.getDeclaredField("openTelemetry");
    f.setAccessible(true);
    return (io.opentelemetry.api.OpenTelemetry) f.get(appender);
  }

  private static void forceNullOpenTelemetry() throws Exception {
    Field f = OpenTelemetryAppender.class.getDeclaredField("openTelemetry");
    f.setAccessible(true);
    for (var root : new Logger[]{(Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)}) {
      root.iteratorForAppenders().forEachRemaining(a -> {
        if (a instanceof OpenTelemetryAppender otel) {
          try {
            f.set(otel, null);
          } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
          }
        }
      });
    }
  }
}
