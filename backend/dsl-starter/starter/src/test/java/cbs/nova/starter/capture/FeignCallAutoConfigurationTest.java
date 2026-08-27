package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import cbs.nova.starter.config.FeignCallConfiguration;
import cbs.nova.starter.core.recorder.ExternalCall;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.core.recorder.RunScopedExternalCallRecorder;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import feign.Body;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class FeignCallConfigurationTest {

  private HttpServer httpServer;
  private String baseUrl;
  private RunScopedExternalCallRecorder recorder;
  private List<ExternalCall> recorded;

  interface StubClient {
    @RequestLine("GET /ping")
    String ping();

    @RequestLine("POST /echo")
    @Headers("Content-Type: text/plain")
    @Body("{body}")
    String echo(String body);
  }

  @BeforeEach
  void setUp() throws IOException {
    httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/", new HttpHandler() {
      @Override
      public void handle(HttpExchange exchange) throws IOException {
        var body = exchange.getRequestBody().readAllBytes();
        exchange.sendResponseHeaders(200, 2);
        try (var out = exchange.getResponseBody()) {
          out.write("ok".getBytes());
        }
      }
    });
    httpServer.start();
    baseUrl = "http://127.0.0.1:" + httpServer.getAddress().getPort();

    recorder = new RunScopedExternalCallRecorder(null);
    recorder.resetGlobalCounts();
    recorded = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    recorder.resetGlobalCounts();
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  @Test
  void interceptorRecordsHttpCalls() {
    var interceptor = new ExternalCallFeignInterceptor(recorder);
    var client = Feign.builder()
            .requestInterceptor(interceptor)
            .target(StubClient.class, baseUrl);

    recorder.startRun("run-1");
    client.ping();
    client.echo("hello");
    recorded.addAll(recorder.finishRun("run-1"));

    assertThat(recorder.getGlobalCounts()).contains(entry(ExternalCallRecorder.TYPE_HTTP, 2));
    assertThat(recorded).hasSize(2);

    var ping = recorded.get(0);
    assertThat(ping.type()).isEqualTo(ExternalCallRecorder.TYPE_HTTP);
    assertThat(ping.operation()).isEqualTo("GET");
    assertThat(ping.target()).contains("/ping");
    assertThat(ping.metadata()).containsKey("payload");
    assertThat(ping.metadata().get("payload")).isInstanceOf(Map.class);

    var pingPayload = (Map<String, Object>) ping.metadata().get("payload");
    assertThat(pingPayload).containsEntry("method", "GET");
    assertThat(pingPayload).containsKey("url");
    assertThat(pingPayload).containsEntry("bodyLength", 0);

    var echo = recorded.get(1);
    assertThat(echo.type()).isEqualTo(ExternalCallRecorder.TYPE_HTTP);
    assertThat(echo.operation()).isEqualTo("POST");
    assertThat(echo.target()).contains("/echo");

    var echoPayload = (Map<String, Object>) echo.metadata().get("payload");
    assertThat(echoPayload).containsEntry("method", "POST");
    assertThat(echoPayload).containsKey("url");
    assertThat(echoPayload).containsEntry("bodyLength", 5);
  }

  @Test
  void autoconfigurationBeanProducesInterceptor() {
    var config = new FeignCallConfiguration();
    var interceptor = config.externalCallFeignInterceptor(recorder);

    assertThat(interceptor).isNotNull();
  }
}
