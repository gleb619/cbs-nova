package cbs.nova.starter.builder;

import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cbs.nova.starter.config.BuilderClientConfiguration;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.dsl.vcs.RepoStatus;
import com.github.benmanes.caffeine.cache.Caffeine;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.CompileModels.CompileResult;
import cbs.nova.starter.model.DslFileModels.BulkWriteRequest;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import cbs.nova.starter.model.VcsModels.CommitRequest;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.DiscardRequest;
import cbs.nova.starter.model.VcsModels.DiscardResult;
import cbs.nova.starter.model.DslFileModels.FileContentResponse;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class DslBuilderClientTest {

  private MockRestServiceServer server;
  private ThreadPoolBulkhead queue;

  @AfterEach
  void tearDown() throws Exception {
    if (server != null) {
      server.verify();
    }
    if (queue != null) {
      queue.close();
    }
  }

  @Test
  void compilePostsSourcesAndReturnsResult() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                    "{\"id\":\"s-1\",\"success\":true,\"generatedFiles\":[\"A.java\"],"
                            + "\"diagnostics\":[],\"durationMillis\":1}",
                    MediaType.APPLICATION_JSON));

    var result = client.compile(new CompileRequest(null, null, null, null, null, null,
            Map.of()));

    assertThat(result.success()).isTrue();
    assertThat(result.generatedFiles()).containsExactly("A.java");
  }

  @Test
  void downloadZipReturnsBytes() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile/s-1/download"))
            .andRespond(withSuccess("bytes", MediaType.APPLICATION_OCTET_STREAM));

    assertThat(client.downloadZip("s-1")).containsExactly("bytes".getBytes());
  }

  @Test
  void listFilesRequestsPrefix() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files?prefix=dsl/"))
            .andRespond(withSuccess(
                    "[{\"path\":\"dsl/Foo.java\",\"sizeBytes\":1,\"lastModifiedMillis\":2}]",
                    MediaType.APPLICATION_JSON));

    var files = client.listFiles("dsl/");

    assertThat(files).hasSize(1);
    assertThat(files.get(0).path()).isEqualTo("dsl/Foo.java");
    assertThat(files.get(0).sizeBytes()).isEqualTo(1L);
    assertThat(files.get(0).lastModifiedMillis()).isEqualTo(2L);
  }

  @Test
  void readFileReturnsContent() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/FooDsl.java"))
            .andRespond(withSuccess(
                    "{\"path\":\"dsl/FooDsl.java\",\"content\":\"class Foo {}\","
                            + "\"pending\":false,\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    var response = client.readFile("dsl/FooDsl.java");

    assertThat(response.content()).isEqualTo("class Foo {}");
  }

  @Test
  void stageWritePostsContent() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/FooDsl.java"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("class Foo {}"))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON));

    client.stageWrite("dsl/FooDsl.java", "class Foo {}");
  }

  @Test
  void stageAllPostsBulkRequest() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/bulk"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("\"files\"")))
            .andRespond(withSuccess(
                    "{\"staged\":1,\"failed\":0}", MediaType.APPLICATION_JSON));

    var result = client.stageAll(List.of(new FileContentRequest("dsl/Foo.java", "v1")));

    assertThat(result.staged()).isEqualTo(1);
    assertThat(result.failed()).isZero();
  }

  @Test
  void vcsStatusReturnsRepoStatus() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/status"))
            .andRespond(withSuccess(
                    "{\"workTree\":\"/repo\",\"dirtyPaths\":[\"dsl/Foo.java\"],"
                            + "\"changes\":{\"dsl/Foo.java\":\"MODIFIED\"}}",
                    MediaType.APPLICATION_JSON));

    Optional<RepoStatus> status = client.vcsStatus();

    assertThat(status).isPresent();
    assertThat(status.get().changeOf("dsl/Foo.java")).isPresent();
  }

  @Test
  void vcsStatus404ReturnsEmpty() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/status"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

    assertThat(client.vcsStatus()).isEmpty();
  }

  @Test
  void commitPostsCommitRequest() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/commit"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess(
                    "{\"commitId\":\"abc\",\"paths\":[\"dsl/Foo.java\"],"
                            + "\"timestampMillis\":42,\"pushed\":true}",
                    MediaType.APPLICATION_JSON));

    var result = client.commit(new CommitRequest(List.of("dsl/Foo.java"), "msg", "a", "a@b"));

    assertThat(result.commitId()).isEqualTo("abc");
    assertThat(result.pushed()).isTrue();
  }

  @Test
  void discardPostsPaths() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/discard"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("dsl/Foo.java")))
            .andRespond(withSuccess(
                    "{\"discarded\":[\"dsl/Foo.java\"]}", MediaType.APPLICATION_JSON));

    var result = client.discard(new DiscardRequest(List.of("dsl/Foo.java")));

    assertThat(result.discarded()).containsExactly("dsl/Foo.java");
  }

  @Test
  void vcsLogRequestsPathAndLimit() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/log?path=dsl/Foo.java&limit=20"))
            .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));

    assertThat(client.vcsLog("dsl/Foo.java", 20)).isEmpty();
  }

  @Test
  void vcsShowRequestsPathAndCommit() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo(
            "http://localhost:8091/api/dsl/vcs/show?path=dsl/Foo.java&commit=abc123"))
            .andRespond(withSuccess("class Foo {}", MediaType.TEXT_PLAIN));

    assertThat(client.vcsShow("dsl/Foo.java", "abc123")).isEqualTo("class Foo {}");
  }

  @Test
  void vcsBranchReadsBranch() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/branch"))
            .andRespond(withSuccess("{\"branch\":\"main\"}", MediaType.APPLICATION_JSON));

    assertThat(client.vcsBranch()).isEqualTo("main");
  }

  @Test
  void vcsBranchReturnsNullWhenResponseHasNoBranch() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/branch"))
            .andRespond(withSuccess("{\"branch\":null}", MediaType.APPLICATION_JSON));

    assertThat(client.vcsBranch()).isNull();
  }

  @Test
  void builderApiExceptionPreservesStatusAndCode() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/FooDsl.java"))
            .andRespond(withStatus(HttpStatus.CONFLICT)
                    .body("{\"code\":\"NOTHING_TO_COMMIT\",\"message\":\"x\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readFile("dsl/FooDsl.java"))
            .isInstanceOf(BuilderApiException.class)
            .satisfies(e -> {
              BuilderApiException bae = (BuilderApiException) e;
              assertThat(bae.getStatusCode().value()).isEqualTo(409);
              assertThat(bae.getCode()).isEqualTo("NOTHING_TO_COMMIT");
            });
  }

  @Test
  void dslCompilationResultReportsDiagnostics() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile"))
            .andRespond(withSuccess(
                    "{\"id\":\"s-1\",\"success\":false,\"generatedFiles\":[],"
                            + "\"diagnostics\":[\"Broken.java:1: bad\"],\"durationMillis\":1}",
                    MediaType.APPLICATION_JSON));

    var result = client.compile(new CompileRequest(null, null, null, null, null, null,
            Map.of()));

    assertThat(result.success()).isFalse();
    assertThat(result.diagnostics()).containsExactly("Broken.java:1: bad");
  }

  @Test
  void circuitBreakerOpensOnUnavailable() {
    var breaker = circuitBreaker(2, 30, 1);
    var client = client(breaker);
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/Foo.java"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/Foo.java"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

    assertThatThrownBy(() -> client.readFile("dsl/Foo.java"))
            .isInstanceOf(BuilderUnavailableException.class);
    await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> assertThatThrownBy(() -> client.readFile("dsl/Foo.java"))
                    .isInstanceOf(BuilderUnavailableException.class)
                    .hasMessageContaining("circuit breaker"));
  }

  @Test
  void queueFullThrowsBusy() throws Exception {
    var breaker = circuitBreaker(100, 30, 100);
    queue = ThreadPoolBulkhead.of("test", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(1)
            .coreThreadPoolSize(1)
            .queueCapacity(0)
            .build());
    Bulkhead bulkhead = Bulkhead.of("test", BulkheadConfig.custom()
            .maxConcurrentCalls(100)
            .maxWaitDuration(Duration.ZERO)
            .build());
    var cache = new BuilderCache(Caffeine.newBuilder().build());
    var rest = BuilderClientConfiguration.configureBuilder(RestClient.builder(),
            DslBuilderClientProperties.builder().build(),
            new BuilderApiErrorHandler(new ObjectMapper()))
            .build();
    var client = new DslBuilderClient(rest, queue, bulkhead, breaker, cache);

    var blocker = new CountDownLatch(1);
    var submitted = client.submit(() -> {
      blocker.await();
      return null;
    });
    try {
      Thread.sleep(50);
      assertThatThrownBy(() -> client.readFile("dsl/Foo.java"))
              .isInstanceOf(BuilderClientBusyException.class)
              .hasMessageContaining("queue is full");
    } finally {
      blocker.countDown();
      submitted.toCompletableFuture().cancel(true);
    }
  }

  private DslBuilderClient client(CircuitBreaker circuitBreaker) {
    var properties = DslBuilderClientProperties.builder().build();
    var errorHandler = new BuilderApiErrorHandler(new ObjectMapper());
    var builder = BuilderClientConfiguration.configureBuilder(RestClient.builder(), properties,
            errorHandler);
    server = MockRestServiceServer.bindTo(builder).build();
    queue = ThreadPoolBulkhead.of("dsl-builder-client-test", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(2)
            .coreThreadPoolSize(2)
            .queueCapacity(10)
            .build());
    return new DslBuilderClient(builder.build(), queue,
            Bulkhead.of("dsl-builder-bulkhead-test", BulkheadConfig.custom()
                    .maxConcurrentCalls(10)
                    .maxWaitDuration(Duration.ofSeconds(5))
                    .build()),
            circuitBreaker,
            new BuilderCache(Caffeine.newBuilder().build()));
  }

  private static CircuitBreaker circuitBreaker(int failureThreshold, long openDurationSeconds,
          int halfOpenProbes) {
    return CircuitBreaker.of("test-breaker", CircuitBreakerConfig.custom()
            .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
            .slidingWindowSize(Math.max(1, failureThreshold))
            .minimumNumberOfCalls(Math.max(1, failureThreshold))
            .failureRateThreshold(100f)
            .permittedNumberOfCallsInHalfOpenState(Math.max(1, halfOpenProbes))
            .waitDurationInOpenState(Duration.ofSeconds(openDurationSeconds))
            .recordException(e -> e instanceof BuilderUnavailableException)
            .ignoreException(e -> !(e instanceof BuilderUnavailableException))
            .build());
  }

}
