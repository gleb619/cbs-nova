package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.ratelimit.InMemoryRateLimitStore;
import cbs.nova.starter.ratelimit.RateLimitStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class InMemoryRateLimitStoreConfiguration {

  @Bean
  @ConditionalOnMissingBean(RateLimitStore.class)
  public RateLimitStore rateLimitStore(CbsSecurityRateLimitProperties properties) {
    if ("redis".equals(properties.store())) {
      throw new IllegalStateException(
              "cbs.security.ratelimit.store=redis requires spring-data-redis on the classpath; "
                      + "add spring-boot-starter-data-redis");
    }
    return new InMemoryRateLimitStore(System::nanoTime);
  }
}
