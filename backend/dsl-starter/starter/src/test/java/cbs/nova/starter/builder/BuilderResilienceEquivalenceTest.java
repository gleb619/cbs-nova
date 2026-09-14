package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cbs.nova.starter.config.BuilderClientConfiguration;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Equivalence tests for the Resilience4j-backed builder resilience stack.
 *
 * <p>
 * Proves that the observable caller-facing behaviour from {@link DslBuilderClient} is preserved
 * after T498: circuit opens on the failure threshold, half-open recovery works, the semaphore
 * bulkhead rejects when saturated, and the thread-pool request queue rejects when full.
 */
class BuilderResilienceEquivalenceTest {

  private MockRestServiceServer server;
  private ThreadPoolBulkhead queue;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.verify();
    }
    if (queue != null) {
      try {
        queue.close();
      } catch (Exception ignored) {
        // best-effort cleanup in tests
      }
    }
  }

  @Test
  void circuitOpensOnFailureThreshold() {
    var config = new BuilderClientConfiguration();
    var breaker = config.dslBuilderCircuitBreaker(
            breakerProperties(1, 30, 3));
    var client = client(breaker);

    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"code\":\"UNAVAILABLE\",\"message\":\"down\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderUnavailableException.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderUnavailableException.class)
            .hasMessageContaining("circuit breaker");
  }

  @Test
  void halfOpenRecoveryClosesBreakerAfterProbesSucceed() throws Exception {
    var config = new BuilderClientConfiguration();
    var breaker = config.dslBuilderCircuitBreaker(
            breakerProperties(1, 1, 1));
    var client = client(breaker);

    // All expectations must be registered before any request is issued.
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"code\":\"UNAVAILABLE\",\"message\":\"down\"}")
                    .contentType(MediaType.APPLICATION_JSON));
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo"))
            .andRespond(withSuccess("{\"name\":\"foo\"}", MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderUnavailableException.class);
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    Thread.sleep(1100);
    assertThat(client.readDraft("foo").name()).isEqualTo("foo");
    assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void bulkheadRejectsWhenSaturated() throws Exception {
    var bulkhead = Bulkhead.of("test", BulkheadConfig.custom()
            .maxConcurrentCalls(1)
            .maxWaitDuration(Duration.ofMillis(100))
            .build());
    var client = client(bulkhead);

    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var first = client.submit(() -> {
      entered.countDown();
      release.await(60, TimeUnit.SECONDS);
      return "first";
    }).toCompletableFuture();
    assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();

    assertThatThrownBy(() -> client.submit(() -> "second").toCompletableFuture().join())
            .isInstanceOfAny(java.util.concurrent.CompletionException.class,
                    java.util.concurrent.ExecutionException.class)
            .rootCause()
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("bulkhead saturated");

    release.countDown();
    assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo("first");
  }

  @Test
  void requestQueueRejectsWhenFull() throws Exception {
    var config = new BuilderClientConfiguration();
    queue = config.dslBuilderQueue(queueProperties(1, 1));
    var client = client(queue);

    var started = new CountDownLatch(1);
    var blocker = new CountDownLatch(1);
    queue.submit(() -> {
      started.countDown();
      blocker.await(10, TimeUnit.SECONDS);
      return "first";
    });
    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
    queue.submit(() -> "queued");

    assertThatThrownBy(() -> client.submit(() -> "overflow"))
            .isInstanceOf(BuilderClientBusyException.class)
            .hasMessageContaining("queue is full");

    blocker.countDown();
  }

  private DslBuilderClientProperties breakerProperties(int failureThreshold,
          long openDurationSeconds, int halfOpenProbes) {
    return DslBuilderClientProperties.builder()
            .breaker(new DslBuilderClientProperties.Breaker(failureThreshold, openDurationSeconds,
                    halfOpenProbes))
            .build();
  }

  private DslBuilderClientProperties queueProperties(int capacity, int workers) {
    return DslBuilderClientProperties.builder()
            .queue(new DslBuilderClientProperties.Queue(capacity, 5000L, workers))
            .build();
  }

  private DslBuilderClient client(CircuitBreaker breaker) {
    return client(Bulkhead.ofDefaults("test"), breaker);
  }

  private DslBuilderClient client(Bulkhead bulkhead) {
    return client(bulkhead, CircuitBreaker.ofDefaults("test"));
  }

  private DslBuilderClient client(ThreadPoolBulkhead queue) {
    return client(queue, Bulkhead.ofDefaults("test"), CircuitBreaker.ofDefaults("test"));
  }

  private DslBuilderClient client(Bulkhead bulkhead, CircuitBreaker breaker) {
    var config = new BuilderClientConfiguration();
    this.queue = config.dslBuilderQueue(DslBuilderClientProperties.builder().build());
    return client(this.queue, bulkhead, breaker);
  }

  private DslBuilderClient client(ThreadPoolBulkhead queue, Bulkhead bulkhead,
          CircuitBreaker breaker) {
    var errorHandler = new BuilderApiErrorHandler(new ObjectMapper());
    var properties = DslBuilderClientProperties.builder().build();
    var builder = BuilderClientConfiguration.configureBuilder(RestClient.builder(), properties,
            errorHandler);
    server = MockRestServiceServer.bindTo(builder).build();
    this.queue = queue;
    return new DslBuilderClient(builder.build(), queue, bulkhead, breaker,
            new BuilderCache(com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build()));
  }

}
