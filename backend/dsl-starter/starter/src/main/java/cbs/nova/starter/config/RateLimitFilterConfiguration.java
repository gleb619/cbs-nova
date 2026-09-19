package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.ratelimit.InMemoryRateLimitStore;
import cbs.nova.starter.ratelimit.RateLimitStore;
import cbs.nova.starter.ratelimit.RedisRateLimitStore;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.web.RateLimitFilter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(CbsSecurityRateLimitProperties.class)
public class RateLimitFilterConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RateLimitFilter rateLimitFilter(CbsSecurityRateLimitProperties properties,
          ObjectMapper objectMapper,
          RateLimitStore rateLimitStore,
          ObjectProvider<ApiKeyStore> apiKeyStoreProvider) {
    ApiKeyStore apiKeyStore = apiKeyStoreProvider.getIfAvailable();
    return new RateLimitFilter(properties, objectMapper, rateLimitStore, apiKeyStore);
  }

  @Bean
  @ConditionalOnMissingBean
  public RateLimitStore rateLimitStore(CbsSecurityRateLimitProperties properties,
          ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
    String store = properties.store();
    if ("redis".equals(store)) {
      RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
      if (factory == null) {
        throw new IllegalStateException(
                "cbs.security.ratelimit.store=redis requires a RedisConnectionFactory; "
                        + "configure spring.data.redis.* or add spring-boot-starter-data-redis");
      }
      return createRedisStore(factory, true);
    }
    if ("memory".equals(store)) {
      return new InMemoryRateLimitStore(System::nanoTime);
    }
    // auto (default): prefer Redis when enabled and a factory is available, otherwise memory.
    // Do not ping Redis here — fail-fast is reserved for the explicit store=redis path.
    RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
    if (factory != null && properties.enabled()) {
      return createRedisStore(factory, false);
    }
    return new InMemoryRateLimitStore(System::nanoTime);
  }

  private static RedisRateLimitStore createRedisStore(RedisConnectionFactory factory,
          boolean validateConnection) {
    StringRedisTemplate template = new StringRedisTemplate(factory);
    template.afterPropertiesSet();
    if (validateConnection) {
      try {
        template.getConnectionFactory().getConnection().ping();
      } catch (DataAccessException e) {
        throw new IllegalStateException(
                "cbs.security.ratelimit.store=redis configured but Redis is unreachable", e);
      }
    }
    return new RedisRateLimitStore(template);
  }

  @Bean
  public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
          RateLimitFilter rateLimitFilter) {
    FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>(
            rateLimitFilter);
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
    registration.addUrlPatterns("/api/*");
    return registration;
  }
}
