package cbs.nova.starter.config;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import ch.qos.logback.classic.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@Configuration
@EnableConfigurationProperties({DryRunProperties.class, CbsNovaCacheProperties.class})
public class DryRunLoggingConfiguration {

  @Bean
  @ConditionalOnMissingBean(DryRunLoggingContext.class)
  @ConditionalOnProperty(name = "cbs.nova.dryRun.context.type", havingValue = "threadlocal", matchIfMissing = true)
  public DryRunLoggingContext dryRunLoggingContext() {
    return new ThreadLocalDryRunLoggingContext();
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLogBufferRegistry.class)
  public DryRunLogBufferRegistry dryRunLogBufferRegistry(CbsNovaCacheProperties cacheProperties) {
    var spec = cacheProperties.specFor(CbsNovaCacheProperties.Names.DRY_RUN_LOG_BUFFERS);
    return new DryRunLogBufferRegistry(spec.ttl(), spec.maxSize());
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLogbackAppender.class)
  public DryRunLogbackAppender dryRunLogbackAppender(
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry) {
    var appender = new DryRunLogbackAppender(dryRunLoggingContext, bufferRegistry);
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
