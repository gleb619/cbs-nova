package cbs.nova.starter.config;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.dsl.logging.ScopedValueDryRunLoggingContext;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Autoconfiguration for dry-run log capture.
 *
 * <p>
 * Provides a {@link DryRunLoggingContext} implementation (defaulting to thread-local, with an
 * opt-in scoped-value variant) and registers the {@link DryRunLogbackAppender} on the Logback root
 * logger under the name {@code DRY_RUN}. The appender is configured from Spring constructor
 * parameters instead of Logback XML.
 */
@AutoConfiguration
@EnableConfigurationProperties(DryRunProperties.class)
public class DryRunLoggingAutoConfiguration {

  /**
   * Default implementation: thread-local, suitable for Temporal multi-node propagation where a
   * worker must restore the runId from an RPC header.
   */
  @Bean
  @ConditionalOnMissingBean(DryRunLoggingContext.class)
  @ConditionalOnProperty(name = "cbs.nova.dryRun.context.type", havingValue = "threadlocal", matchIfMissing = true)
  public DryRunLoggingContext dryRunLoggingContext() {
    return new ThreadLocalDryRunLoggingContext();
  }

  /**
   * Optional scoped-value implementation for callers that can bind the runId to a
   * {@link ScopedValue} carrier block.
   */
  @Bean
  @ConditionalOnProperty(name = "cbs.nova.dryRun.context.type", havingValue = "scoped")
  public DryRunLoggingContext scopedDryRunLoggingContext() {
    return new ScopedValueDryRunLoggingContext();
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLogbackAppender.class)
  public DryRunLogbackAppender dryRunLogbackAppender(
          DryRunLoggingContext dryRunLoggingContext,
          DryRunProperties properties) {
    var appender = new DryRunLogbackAppender(dryRunLoggingContext,
            properties.log().maxEventsPerRun());
    appender.setName("DRY_RUN");
    return appender;
  }

  @Bean
  public ApplicationRunner dryRunLogbackAppenderInstaller(DryRunLogbackAppender appender) {
    return _ -> {
      var root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
      if (root.getAppender("DRY_RUN") == null) {
        appender.setContext(root.getLoggerContext());
        if (!appender.isStarted()) {
          appender.start();
        }
        root.addAppender(appender);
      }
    };
  }

}
