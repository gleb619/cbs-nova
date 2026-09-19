package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.ratelimit.InMemoryRateLimitStore;
import cbs.nova.starter.ratelimit.RateLimitStore;
import cbs.nova.starter.ratelimit.RedisRateLimitStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(RedisConnectionFactory.class)
public class RedisRateLimitStoreConfiguration {

  @Bean
  @ConditionalOnMissingBean(RateLimitStore.class)
  public RateLimitStore rateLimitStore(CbsSecurityRateLimitProperties properties,
          ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
    String store = properties.store();
    switch (store) {
      case "redis" -> {
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory == null) {
          throw new IllegalStateException(
                  "cbs.security.ratelimit.store=redis requires a RedisConnectionFactory; "
                          + "configure spring.data.redis.* or add spring-boot-starter-data-redis");
        }
        return createRedisStore(factory, true);
      }
      case "memory" -> {
        return new InMemoryRateLimitStore(System::nanoTime);
      }
      case null, default -> {
        // auto (default): prefer Redis when enabled and a factory is available, otherwise memory.
        // Do not ping Redis here — fail-fast is reserved for the explicit store=redis path.
        RedisConnectionFactory factory = redisConnectionFactoryProvider.getIfAvailable();
        if (factory != null && properties.enabled()) {
          return createRedisStore(factory, false);
        }
        return new InMemoryRateLimitStore(System::nanoTime);
      }
    }
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
}
