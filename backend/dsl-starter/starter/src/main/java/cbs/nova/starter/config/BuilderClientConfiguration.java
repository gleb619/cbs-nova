package cbs.nova.starter.config;

import cbs.nova.starter.builder.BuilderBulkhead;
import cbs.nova.starter.builder.BuilderCircuitBreaker;
import cbs.nova.starter.builder.BuilderRequestQueue;
import cbs.nova.starter.builder.DslBuilderClient;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(DslBuilderClientProperties.class)
@ConditionalOnProperty(prefix = "csb.dsl.builder-client", name = "enabled", havingValue = "true", matchIfMissing = true)
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
  public BuilderCircuitBreaker builderCircuitBreaker(DslBuilderClientProperties properties) {
    var breaker = properties.breaker();
    return new BuilderCircuitBreaker(breaker.failureThreshold(),
            Duration.ofSeconds(breaker.openDurationSeconds()).toMillis(), breaker.halfOpenProbes(),
            System::currentTimeMillis);
  }

  @Bean
  public BuilderBulkhead builderBulkhead(DslBuilderClientProperties properties) {
    var bulkhead = properties.bulkhead();
    return new BuilderBulkhead(new Semaphore(Math.max(1, bulkhead.permits())),
            bulkhead.acquireTimeoutSeconds());
  }

  @Bean
  public BuilderRequestQueue builderRequestQueue(DslBuilderClientProperties properties) {
    var queue = properties.queue();
    return new BuilderRequestQueue(queue.capacity(), queue.offerTimeoutMillis(), queue.workers());
  }

  @Bean
  public DslBuilderClient dslBuilderClient(RestClient dslBuilderRestClient,
          BuilderRequestQueue builderRequestQueue, BuilderBulkhead builderBulkhead,
          BuilderCircuitBreaker builderCircuitBreaker) {
    return new DslBuilderClient(dslBuilderRestClient, builderRequestQueue, builderBulkhead,
            builderCircuitBreaker);
  }

}
