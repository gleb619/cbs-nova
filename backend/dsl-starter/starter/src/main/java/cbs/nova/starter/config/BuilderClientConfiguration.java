package cbs.nova.starter.config;

import cbs.nova.starter.builder.BuilderCache;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.CbsNovaCacheProperties;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.BuilderUnavailableException;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(DslBuilderClientProperties.class)
public class BuilderClientConfiguration {

  @Bean
  public RestClient dslBuilderRestClient(ObjectProvider<RestClient.Builder> builderProvider,
          DslBuilderClientProperties properties, BuilderApiErrorHandler errorHandler) {
    var builder = builderProvider.getIfAvailable(RestClient::builder);
    return configureBuilder(builder, properties, errorHandler).build();
  }

  public static RestClient.Builder configureBuilder(RestClient.Builder builder,
          DslBuilderClientProperties properties, BuilderApiErrorHandler errorHandler) {
    return builder.baseUrl(properties.baseUrl())
            .requestFactory(jdkRequestFactory(properties))
            .defaultStatusHandler(HttpStatusCode::isError, errorHandler::handle);
  }

  public static JdkClientHttpRequestFactory jdkRequestFactory(
          DslBuilderClientProperties properties) {
    var factory = new JdkClientHttpRequestFactory(httpClient(properties));
    factory.setReadTimeout(Duration.ofMillis(properties.timeouts().readMillis()));
    return factory;
  }

  public static HttpClient httpClient(DslBuilderClientProperties properties) {
    return HttpClient.newBuilder()
            .version(properties.http2() ? HttpClient.Version.HTTP_2 : HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofMillis(properties.timeouts().connectMillis()))
            .build();
  }

  @Bean
  public BuilderApiErrorHandler builderApiErrorHandler(ObjectMapper objectMapper) {
    return new BuilderApiErrorHandler(objectMapper);
  }

  @Bean
  public CircuitBreaker dslBuilderCircuitBreaker(DslBuilderClientProperties properties) {
    var breaker = properties.breaker();
    return CircuitBreaker.of("dsl-builder-breaker", CircuitBreakerConfig.custom()
            // Count-based sliding window sized to the configured failure threshold. With a
            // 100% failure-rate threshold, the breaker opens when the last N calls all failed
            // with a recorded exception, approximating the previous consecutive-count semantics.
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(Math.max(1, breaker.failureThreshold()))
            .minimumNumberOfCalls(Math.max(1, breaker.failureThreshold()))
            .failureRateThreshold(100f)
            .permittedNumberOfCallsInHalfOpenState(Math.max(1, breaker.halfOpenProbes()))
            .waitDurationInOpenState(Duration.ofSeconds(breaker.openDurationSeconds()))
            // Only BuilderUnavailableException counts as a failure; everything else is ignored so
            // user errors or HTTP 4xx responses do not trip the breaker.
            .recordException(e -> e instanceof BuilderUnavailableException)
            .ignoreException(e -> !(e instanceof BuilderUnavailableException))
            .build());
  }

  @Bean
  public Bulkhead dslBuilderBulkhead(DslBuilderClientProperties properties) {
    var bulkhead = properties.bulkhead();
    return Bulkhead.of("dsl-builder-bulkhead", BulkheadConfig.custom()
            .maxConcurrentCalls(Math.max(1, bulkhead.permits()))
            .maxWaitDuration(Duration.ofSeconds(bulkhead.acquireTimeoutSeconds()))
            .build());
  }

  @Bean(destroyMethod = "close")
  public ThreadPoolBulkhead dslBuilderQueue(DslBuilderClientProperties properties) {
    var queue = properties.queue();
    return ThreadPoolBulkhead.of("dsl-builder-queue", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(Math.max(1, queue.workers()))
            .coreThreadPoolSize(Math.max(1, queue.workers()))
            .queueCapacity(Math.max(1, queue.capacity()))
            // Resilience4j's ThreadPoolBulkhead rejects immediately when the bounded queue is full;
            // it does not support an offer timeout like the previous hand-rolled queue. The
            // offerTimeoutMillis property is retained for backward compatibility but is ignored.
            .build());
  }

  @Bean
  public BuilderCache builderCache(CbsNovaCacheProperties cacheProperties) {
    var spec = cacheProperties.specFor(StarterConstants.BUILDER_READS);
    return new BuilderCache(Caffeine.newBuilder()
            .expireAfterWrite(spec.ttl())
            .maximumSize(spec.maxSize())
            .recordStats()
            .build());
  }

  @Bean
  public DslBuilderClient dslBuilderClient(RestClient dslBuilderRestClient,
          ThreadPoolBulkhead dslBuilderQueue, Bulkhead dslBuilderBulkhead,
          CircuitBreaker dslBuilderCircuitBreaker, BuilderCache builderCache) {
    return new DslBuilderClient(dslBuilderRestClient, dslBuilderQueue, dslBuilderBulkhead,
            dslBuilderCircuitBreaker, builderCache);
  }

}
