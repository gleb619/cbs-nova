package cbs.nova.starter.config;

import cbs.nova.dsl.logging.DryRunLoggingContext;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.DryRunProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.logging.DryRunLogBufferRegistry;
import cbs.nova.starter.logging.DryRunLogEventPublisher;
import cbs.nova.starter.logging.DryRunLogbackAppender;
import cbs.nova.starter.logging.MdcDryRunLoggingContext;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import ch.qos.logback.classic.Logger;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
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
  @ConditionalOnProperty(name = "cbs.nova.dryRun.context.type", havingValue = "mdc", matchIfMissing = true)
  public DryRunLoggingContext dryRunLoggingContext() {
    return new MdcDryRunLoggingContext();
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLoggingContext.class)
  @ConditionalOnProperty(name = "cbs.nova.dryRun.context.type", havingValue = "threadlocal")
  public DryRunLoggingContext threadLocalDryRunLoggingContext() {
    return new ThreadLocalDryRunLoggingContext();
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLogBufferRegistry.class)
  public DryRunLogBufferRegistry dryRunLogBufferRegistry(CbsNovaCacheProperties cacheProperties) {
    var spec = cacheProperties.specFor(StarterConstants.DRY_RUN_LOG_BUFFERS);
    return new DryRunLogBufferRegistry(Caffeine.newBuilder()
            .expireAfterAccess(spec.ttl())
            .maximumSize(spec.maxSize())
            .build());
  }

  @Bean
  @ConditionalOnMissingBean(DryRunLogbackAppender.class)
  public DryRunLogbackAppender dryRunLogbackAppender(
          DryRunLoggingContext dryRunLoggingContext,
          DryRunLogBufferRegistry bufferRegistry,
          ObjectProvider<DryRunLogEventPublisher> publishers) {
    var appender = new DryRunLogbackAppender(dryRunLoggingContext, bufferRegistry,
            publishers.getIfAvailable());
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
