package cbs.nova.starter.builder;

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
import cbs.nova.starter.service.DslGitStatusResolver.RepoStatus;
import com.github.benmanes.caffeine.cache.Caffeine;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.CompileModels.CompileRequest;
import cbs.nova.starter.model.VcsModels.DraftRequest;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
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
  void tearDown() {
    if (server != null) {
      server.verify();
    }
    if (queue != null) {
      try {
        queue.close();
      } catch (Exception ignored) {
      }
    }
  }

  @Test
  void compilePostsSourcesAndReturnsResult() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("\"sources\"")))
            .andExpect(content().string(containsString("LoanDsl.java")))
            .andRespond(withSuccess(
                    "{\"id\":\"s-1\",\"success\":true,\"generatedFiles\":[\"LoanDsl.java\"],"
                            + "\"diagnostics\":[],\"durationMillis\":42}",
                    MediaType.APPLICATION_JSON));

    var result = client.compile(new CompileRequest(null, null, null, null, null, null,
            Map.of("LoanDsl.java", "class LoanDsl {}")));

    assertThat(result.id()).isEqualTo("s-1");
    assertThat(result.success()).isTrue();
    assertThat(result.generatedFiles()).containsExactly("LoanDsl.java");
  }

  @Test
  void downloadZipReturnsBytes() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile/s-1/download"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("zip-bytes", MediaType.parseMediaType("application/zip")));

    assertThat(client.downloadZip("s-1")).isEqualTo("zip-bytes".getBytes());
  }

  @Test
  void saveDraftSerializesDraftRequest() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo/save"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string(containsString("\"name\":\"foo\"")))
            .andRespond(withSuccess(
                    "{\"name\":\"foo\",\"status\":\"Draft\",\"location\":\"/ws/.workbench/drafts/foo.json\","
                            + "\"reloaded\":false,\"loadResult\":{\"processes\":[],\"transactions\":[],"
                            + "\"functions\":[]}}",
                    MediaType.APPLICATION_JSON));

    var response = client.saveDraft("foo",
            new DraftRequest("foo", "process", "Draft", "1", "q"));

    assertThat(response.name()).isEqualTo("foo");
    assertThat(response.status()).isEqualTo("Draft");
    assertThat(response.reloaded()).isFalse();
    assertThat(response.loadResult().total()).isZero();
  }

  @Test
  void listDraftsDeserializesPageResponse() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts?limit=50&offset=0"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess(
                    "{\"items\":[{\"name\":\"foo\",\"type\":\"process\",\"status\":\"Draft\","
                            + "\"version\":\"1\",\"updatedAt\":123}],\"total\":1,\"offset\":0,\"limit\":50}",
                    MediaType.APPLICATION_JSON));

    var page = client.listDrafts(50, 0);

    assertThat(page.total()).isEqualTo(1);
    assertThat(page.items()).hasSize(1);
    assertThat(page.items().get(0).name()).isEqualTo("foo");
    assertThat(page.items().get(0).updatedAt()).isEqualTo(123);
  }

  @Test
  void stageWritePostsRawContentToWildcardPath() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/dsl/LoanDsl.java"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("class LoanDsl {}"))
            .andRespond(withStatus(HttpStatus.ACCEPTED));

    client.stageWrite("dsl/LoanDsl.java", "class LoanDsl {}");
  }

  @Test
  void pendingCountReadsStatusEndpoint() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/files/status"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"pending\":3}", MediaType.APPLICATION_JSON));

    assertThat(client.pendingCount()).isEqualTo(3);
  }

  @Test
  void vcsStatusReturnsRepoStatus() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/status"))
            .andExpect(method(HttpMethod.GET))
            .andRespond(withSuccess("{\"workTree\":\"/repo\",\"dirtyPaths\":[\"a.java\"]}",
                    MediaType.APPLICATION_JSON));

    Optional<RepoStatus> status = client.vcsStatus();

    assertThat(status).isPresent();
    assertThat(status.get().workTree().toString()).isEqualTo("/repo");
    assertThat(status.get().dirtyPaths()).containsExactly("a.java");
  }

  @Test
  void vcsStatusReturnsEmptyOn404() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/vcs/status"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                    .body("{\"code\":\"NOT_FOUND\",\"message\":\"no git repository\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThat(client.vcsStatus()).isEmpty();
  }

  @Test
  void missingDraftMapsToBuilderApiException() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/missing"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND)
                    .body("{\"code\":\"NOT_FOUND\",\"message\":\"Draft not found: missing\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readDraft("missing"))
            .isInstanceOf(BuilderApiException.class)
            .satisfies(ex -> {
              var api = (BuilderApiException) ex;
              assertThat(api.getStatusCode().value()).isEqualTo(404);
              assertThat(api.getCode()).isEqualTo("NOT_FOUND");
            });
  }

  @Test
  void compileFailureMapsToDslCompilationException() {
    var client = client(circuitBreaker(5, 30, 3));
    server.expect(requestTo("http://localhost:8091/api/dsl/compile"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body("{\"error\":\"COMPILE_FAILED\",\"diagnostics\":[\"Broken.java: bad syntax\"]}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.compile(new CompileRequest(null, null, null, null, null, null,
            Map.of("Broken.java", "broken"))))
            .isInstanceOf(DslCompilationException.class)
            .satisfies(ex -> {
              var diagnostics = ((DslCompilationException) ex).diagnostics();
              assertThat(diagnostics).hasSize(1);
              assertThat(diagnostics.get(0).message()).isEqualTo("Broken.java: bad syntax");
              assertThat(diagnostics.get(0).severity()).isEqualTo("error");
            });
  }

  @Test
  void builderBusyMapsToBusyExceptionWithoutTrippingBreaker() {
    var circuit = circuitBreaker(5, 30, 3);
    var client = client(circuit);
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo"))
            .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                    .body("{\"error\":\"BUILDER_BUSY\",\"message\":\"queue full\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderClientBusyException.class);
    assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
  }

  @Test
  void serverErrorTripsBreakerAndShortCircuitsNextCall() {
    var circuit = circuitBreaker(1, 30, 3);
    var client = client(circuit);
    server.expect(requestTo("http://localhost:8091/api/dsl/drafts/foo"))
            .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("{\"code\":\"BULKHEAD_SATURATED\",\"message\":\"saturated\"}")
                    .contentType(MediaType.APPLICATION_JSON));

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderUnavailableException.class);
    assertThat(circuit.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    assertThatThrownBy(() -> client.readDraft("foo"))
            .isInstanceOf(BuilderUnavailableException.class)
            .hasMessageContaining("circuit breaker");
  }

  @Test
  void jdkFactoryUsesHttp2ByDefaultForPriorKnowledge() {
    var httpClient = BuilderClientConfiguration.httpClient(
            DslBuilderClientProperties.builder().build());

    assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_2);
  }

  @Test
  void jdkFactoryHonorsHttp1DisabledFlag() {
    var httpClient = BuilderClientConfiguration.httpClient(
            DslBuilderClientProperties.builder().http2(false).build());

    assertThat(httpClient.version()).isEqualTo(HttpClient.Version.HTTP_1_1);
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
