package cbs.nova.starter.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
class RedisRateLimitStoreTest {

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(
          DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

  private StringRedisTemplate newRedisTemplate() {
    LettuceConnectionFactory factory = new LettuceConnectionFactory(REDIS.getHost(),
            REDIS.getMappedPort(6379));
    factory.afterPropertiesSet();
    StringRedisTemplate template = new StringRedisTemplate(factory);
    template.afterPropertiesSet();
    return template;
  }

  @Test
  void limitsPrincipalAcrossTwoStoreInstances() {
    String key = "tenant-a|/api/dsl/run/**";
    RateLimit limit = new RateLimit(2, 1.0);

    RedisRateLimitStore first = new RedisRateLimitStore(newRedisTemplate());
    assertThat(first.consume(key, limit).allowed()).isTrue();
    assertThat(first.consume(key, limit).allowed()).isTrue();

    RedisRateLimitStore second = new RedisRateLimitStore(newRedisTemplate());
    assertThat(second.consume(key, limit).allowed()).isFalse();
  }

  @Test
  void restartingStoreDoesNotResetBucket() {
    String key = "tenant-b|/api/dsl/run/**";
    RateLimit limit = new RateLimit(1, 1.0);

    RedisRateLimitStore beforeRestart = new RedisRateLimitStore(newRedisTemplate());
    assertThat(beforeRestart.consume(key, limit).allowed()).isTrue();
    assertThat(beforeRestart.consume(key, limit).allowed()).isFalse();

    RedisRateLimitStore afterRestart = new RedisRateLimitStore(newRedisTemplate());
    assertThat(afterRestart.consume(key, limit).allowed()).isFalse();
  }

  @Test
  void differentPrincipalsHaveIndependentBuckets() {
    RateLimit limit = new RateLimit(1, 1.0);

    RedisRateLimitStore store = new RedisRateLimitStore(newRedisTemplate());
    assertThat(store.consume("principal-a|/api/dsl/run/**", limit).allowed()).isTrue();
    assertThat(store.consume("principal-a|/api/dsl/run/**", limit).allowed()).isFalse();
    assertThat(store.consume("principal-b|/api/dsl/run/**", limit).allowed()).isTrue();
  }

  @Test
  void differentRouteClassesHaveIndependentBuckets() {
    RateLimit limit = new RateLimit(1, 1.0);

    RedisRateLimitStore store = new RedisRateLimitStore(newRedisTemplate());
    assertThat(store.consume("tenant-c|/api/dsl/run/**", limit).allowed()).isTrue();
    assertThat(store.consume("tenant-c|/api/dsl/run/**", limit).allowed()).isFalse();
    assertThat(store.consume("tenant-c|/api/dsl/preview/**", limit).allowed()).isTrue();
  }
}
