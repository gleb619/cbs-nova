package cbs.nova.starter.capture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.config.FeignCallAutoConfiguration;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import feign.Body;
import feign.Feign;
import feign.Headers;
import feign.RequestLine;
import feign.Target;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Map;

class FeignCallAutoConfigurationTest {

  private HttpServer httpServer;
  private String baseUrl;
  private ExternalCallTracker tracker;
  private ArrayList<ExternalCallTracker.CallDetail> recorded;

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

    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    recorded = new ArrayList<>();
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.resetGlobalCounts();
    if (httpServer != null) {
      httpServer.stop(0);
    }
  }

  @Test
  void interceptorRecordsHttpCalls() {
    var interceptor = new ExternalCallFeignInterceptor(tracker);
    var client = Feign.builder()
            .requestInterceptor(interceptor)
            .target(StubClient.class, baseUrl);

    tracker.startTracking(recorded);
    client.ping();
    client.echo("hello");
    tracker.stopTracking();

    assertThat(tracker.getGlobalCounts()).contains(entry(ExternalCallTracker.TYPE_HTTP, 2));
    assertThat(recorded).hasSize(2);

    var ping = recorded.get(0);
    assertThat(ping.type()).isEqualTo(ExternalCallTracker.TYPE_HTTP);
    assertThat(ping.operation()).isEqualTo("GET");
    assertThat(ping.target()).contains("/ping");
    assertThat(ping.metadata()).containsKey("payload");
    assertThat(ping.metadata().get("payload")).isInstanceOf(Map.class);

    var pingPayload = (Map<String, Object>) ping.metadata().get("payload");
    assertThat(pingPayload).containsEntry("method", "GET");
    assertThat(pingPayload).containsKey("url");
    assertThat(pingPayload).containsEntry("bodyLength", 0);

    var echo = recorded.get(1);
    assertThat(echo.type()).isEqualTo(ExternalCallTracker.TYPE_HTTP);
    assertThat(echo.operation()).isEqualTo("POST");
    assertThat(echo.target()).contains("/echo");

    var echoPayload = (Map<String, Object>) echo.metadata().get("payload");
    assertThat(echoPayload).containsEntry("method", "POST");
    assertThat(echoPayload).containsKey("url");
    assertThat(echoPayload).containsEntry("bodyLength", 5);
  }

  @Test
  void autoconfigurationBeanProducesInterceptor() {
    var config = new FeignCallAutoConfiguration();
    var interceptor = config.externalCallFeignInterceptor(tracker);

    assertThat(interceptor).isNotNull();
  }
}
