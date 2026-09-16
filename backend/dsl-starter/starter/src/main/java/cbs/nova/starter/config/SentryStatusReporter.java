package cbs.nova.starter.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class SentryStatusReporter {

  @Bean(name = "cbsNovaSentryStatusReporter")
  SmartInitializingSingleton sentryStatusReporter(Environment environment) {
    return () -> {
      String dsn = environment.getProperty("sentry.dsn", "");
      boolean active = dsn != null && !dsn.isBlank();
      if (active) {
        log.info("Sentry error tracking ACTIVE (environment: {})",
            environment.getProperty("sentry.environment", "unknown"));
      } else {
        log.info("Sentry error tracking INERT (no DSN configured)");
      }
    };
  }
}
