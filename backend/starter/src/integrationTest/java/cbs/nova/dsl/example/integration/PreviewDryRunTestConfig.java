package cbs.nova.dsl.example.integration;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import feign.Feign;
import feign.Param;
import feign.RequestLine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Test configuration for the preview dry-run integration test. Provides a helper that performs real
 * JDBC and Feign side effects in {@code execute(...)} and a mocked, non-side-effecting preview path
 * in {@code preview(...)}. A small JDK {@link HttpServer} acts as the HTTP target.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PreviewDryRunTestConfig {

  public record PreviewSideEffectsIn(String requestId, String payload) {
  }

  public record PreviewSideEffectsOut(String result, String source) {
  }

  public interface PreviewDryRunHttpApi {

    @RequestLine("GET /probe/{requestId}")
    String probe(@Param("requestId") String requestId);
  }

  @Bean
  AtomicInteger previewDryRunHttpRequestCount() {
    return new AtomicInteger(0);
  }

  @Bean
  PreviewDryRunHttpServer previewDryRunHttpServer(
          AtomicInteger previewDryRunHttpRequestCount) throws IOException {
    HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
            "/probe",
            exchange -> {
              if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                previewDryRunHttpRequestCount.incrementAndGet();
              }
              byte[] body = "stubbed".getBytes(StandardCharsets.UTF_8);
              exchange.sendResponseHeaders(200, body.length);
              try (var out = exchange.getResponseBody()) {
                out.write(body);
              }
            });
    server.start();
    return new PreviewDryRunHttpServer(server);
  }

  public static final class PreviewDryRunHttpServer implements AutoCloseable {

    private final HttpServer server;

    public PreviewDryRunHttpServer(HttpServer server) {
      this.server = server;
    }

    public HttpServer server() {
      return server;
    }

    @Override
    public void close() {
      server.stop(0);
    }
  }

  @Bean
  PreviewDryRunHttpApi previewDryRunHttpApi(
          cbs.nova.starter.capture.ExternalCallFeignInterceptor externalCallFeignInterceptor,
          PreviewDryRunHttpServer previewDryRunHttpServer) {
    String baseUrl = "http://localhost:" + previewDryRunHttpServer.server().getAddress().getPort();
    return Feign.builder()
            .requestInterceptor(externalCallFeignInterceptor)
            .target(PreviewDryRunHttpApi.class, baseUrl);
  }

  @Bean
  PreviewSideEffectsHelper previewSideEffectsHelper(
          JdbcTemplate jdbcTemplate,
          PreviewDryRunHttpApi previewDryRunHttpApi,
          ExternalCallRecorder externalCallRecorder) {
    return new PreviewSideEffectsHelper(jdbcTemplate, previewDryRunHttpApi, externalCallRecorder);
  }

  @Helper(name = "previewSideEffectsHelper")
  public static final class PreviewSideEffectsHelper
          implements
            Executable<PreviewSideEffectsIn, PreviewSideEffectsOut> {

    private static final Logger log = LoggerFactory.getLogger(PreviewSideEffectsHelper.class);
    private final JdbcTemplate jdbcTemplate;
    private final PreviewDryRunHttpApi httpApi;
    private final ExternalCallRecorder externalCallRecorder;

    public PreviewSideEffectsHelper(
            JdbcTemplate jdbcTemplate,
            PreviewDryRunHttpApi httpApi,
            ExternalCallRecorder externalCallRecorder) {
      this.jdbcTemplate = jdbcTemplate;
      this.httpApi = httpApi;
      this.externalCallRecorder = externalCallRecorder;
    }

    @Override
    public Result<PreviewSideEffectsOut> execute(Context<PreviewSideEffectsIn> ctx) {
      PreviewSideEffectsIn input = ctx.body();
      ensureTable();
      jdbcTemplate.update(
              "INSERT INTO preview_dry_run (request_id, payload) VALUES (?, ?)",
              input.requestId(),
              input.payload());
      String response = httpApi.probe(input.requestId());
      return Result
              .success(new PreviewSideEffectsOut("real:" + input.requestId(), "http:" + response));
    }

    @Override
    public Result<PreviewSideEffectsOut> preview(Context<PreviewSideEffectsIn> ctx) {
      PreviewSideEffectsIn input = ctx.body();
      log.info("Preview dry-run for requestId={}", input.requestId());
      ensureTable();
      jdbcTemplate.queryForObject("SELECT COUNT(*) FROM preview_dry_run", Integer.class);
      externalCallRecorder.record(
              "http",
              "http://localhost/probe",
              "GET",
              Map.of("url", "/probe/" + input.requestId()));
      return Result.success(new PreviewSideEffectsOut("preview-mock", "none"));
    }

    private void ensureTable() {
      jdbcTemplate.execute(
              "CREATE TABLE IF NOT EXISTS preview_dry_run ("
                      + "request_id VARCHAR(255) PRIMARY KEY, payload VARCHAR(255))");
    }
  }
}
