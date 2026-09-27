package cbs.nova.starter.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cbs.nova.starter.config.BuilderClientConfiguration;
import cbs.nova.starter.config.properties.DslBuilderClientProperties;
import cbs.nova.starter.controller.BuilderApiErrorHandler;
import cbs.nova.starter.model.DslFileModels.FileContentRequest;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.ThreadPoolBulkhead;
import io.github.resilience4j.bulkhead.ThreadPoolBulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

class DslBuilderDocsClientTest {

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
  void listDocsRequestsPrefix() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs?prefix=guides/"))
            .andRespond(withSuccess(
                    "[{\"path\":\"guides/a.md\",\"sizeBytes\":1,\"lastModifiedMillis\":2}]",
                    MediaType.APPLICATION_JSON));

    var files = client.listDocs("guides/");

    assertThat(files).hasSize(1);
    assertThat(files.get(0).path()).isEqualTo("guides/a.md");
  }

  @Test
  void readDocReturnsContent() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/foo-bar.md"))
            .andRespond(withSuccess(
                    "{\"path\":\"foo-bar.md\",\"content\":\"# Foo\",\"pending\":false,"
                            + "\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    var response = client.readDoc("foo-bar.md");

    assertThat(response.content()).isEqualTo("# Foo");
  }

  @Test
  void readDocByNameRequestsByNameEndpoint() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/by-name/FooBar"))
            .andRespond(withSuccess(
                    "{\"path\":\"foo-bar.md\",\"content\":\"# Foo\",\"pending\":false,"
                            + "\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    var response = client.readDocByName("FooBar");

    assertThat(response.path()).isEqualTo("foo-bar.md");
    assertThat(response.content()).isEqualTo("# Foo");
  }

  @Test
  void stageDocWriteByNamePostsStructuredContentAndReturnsResolvedPath() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/by-name/FooBar"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("# Foo"))
            .andRespond(withSuccess(
                    "{\"path\":\"foo-bar.md\",\"content\":\"# Foo\",\"pending\":true,"
                            + "\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    var result = client.stageDocWriteByName("FooBar", "# Foo");

    assertThat(result.path()).isEqualTo("foo-bar.md");
  }

  @Test
  void readDocCachesSubsequentReads() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/foo-bar.md"))
            .andRespond(withSuccess(
                    "{\"path\":\"foo-bar.md\",\"content\":\"# Foo\",\"pending\":false,"
                            + "\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    client.readDoc("foo-bar.md");
    var cached = client.readDoc("foo-bar.md");

    assertThat(cached.content()).isEqualTo("# Foo");
  }

  @Test
  void docExistsReturnsBoolean() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/exists/foo-bar.md"))
            .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));

    assertThat(client.docExists("foo-bar.md")).isTrue();
  }

  @Test
  void stageDocWritePostsContentAndInvalidatesReadCache() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/foo-bar.md"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(content().string("# Foo"))
            .andRespond(withSuccess());
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/foo-bar.md"))
            .andRespond(withSuccess(
                    "{\"path\":\"foo-bar.md\",\"content\":\"# Foo\",\"pending\":true,"
                            + "\"crc32\":12}",
                    MediaType.APPLICATION_JSON));

    client.stageDocWrite("foo-bar.md", "# Foo");
    var response = client.readDoc("foo-bar.md");

    assertThat(response.content()).isEqualTo("# Foo");
  }

  @Test
  void stageDocAllPostsBulkRequest() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/bulk"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"staged\":1,\"failed\":0}",
                    MediaType.APPLICATION_JSON));

    var result = client.stageDocAll(List.of(new FileContentRequest("a.md", "# A")));

    assertThat(result.staged()).isEqualTo(1);
  }

  @Test
  void flushDocsPostsFlushRequest() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/flush"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("{\"flushed\":1,\"failed\":0}",
                    MediaType.APPLICATION_JSON));

    var result = client.flushDocs();

    assertThat(result.flushed()).isEqualTo(1);
  }

  @Test
  void docsPendingCountReadsStatus() {
    var client = client();
    server.expect(requestTo("http://localhost:8091/api/dsl/docs/status"))
            .andRespond(withSuccess("{\"pending\":3}", MediaType.APPLICATION_JSON));

    assertThat(client.docsPendingCount()).isEqualTo(3);
  }

  private DslBuilderDocsClient client() {
    var properties = DslBuilderClientProperties.builder().build();
    var errorHandler = new BuilderApiErrorHandler(new ObjectMapper());
    var builder = BuilderClientConfiguration.configureBuilder(RestClient.builder(), properties,
            errorHandler);
    server = MockRestServiceServer.bindTo(builder).build();
    queue = ThreadPoolBulkhead.of("dsl-builder-docs-client-test", ThreadPoolBulkheadConfig.custom()
            .maxThreadPoolSize(2)
            .coreThreadPoolSize(2)
            .queueCapacity(10)
            .build());
    var executor = new BuilderRequestExecutor(queue,
            Bulkhead.of("dsl-builder-docs-bulkhead-test", BulkheadConfig.custom()
                    .maxConcurrentCalls(10)
                    .maxWaitDuration(Duration.ofSeconds(5))
                    .build()),
            CircuitBreaker.ofDefaults("dsl-builder-docs-test"));
    return new DslBuilderDocsClient(builder.build(), executor,
            new BuilderCache(Caffeine.newBuilder().build()));
  }
}
