package cbs.nova.starter.config.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.router.DslDocsRouterConfiguration;
import cbs.nova.starter.config.router.DslFileRouterConfiguration;
import cbs.nova.starter.controller.DslDocsHandler;
import cbs.nova.starter.controller.DslFileHandler;
import cbs.nova.starter.model.DslFileModels.PendingWritesStatus;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class DslFileRouterConfigurationTest {

  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of(
          new StringHttpMessageConverter(),
          new JacksonJsonHttpMessageConverter());

  private DslFileHandler fileHandler;
  private DslDocsHandler docsHandler;
  private RouterFunction<ServerResponse> fileRouter;
  private RouterFunction<ServerResponse> docsRouter;

  @BeforeEach
  void setUp() {
    fileHandler = mock(DslFileHandler.class);
    docsHandler = mock(DslDocsHandler.class);
    when(fileHandler.status(any())).thenReturn(ok(new PendingWritesStatus(0)));
    when(fileHandler.flush(any())).thenReturn(ServerResponse.ok().build());
    when(docsHandler.status(any())).thenReturn(ok(new PendingWritesStatus(0)));
    when(docsHandler.flush(any())).thenReturn(ServerResponse.ok().build());
    fileRouter = new DslFileRouterConfiguration().dslFileRouter(fileHandler);
    docsRouter = new DslDocsRouterConfiguration().dslDocsRouter(docsHandler);
  }

  @Test
  void fileStatusRoutesToStatusHandler() throws Exception {
    var routed = fileRouter.route(request("GET", "/api/dsl/files/status"));

    assertThat(routed).isPresent();
    routed.get().handle(request("GET", "/api/dsl/files/status"));
    verify(fileHandler).status(any());
    verify(fileHandler, never()).read(any());
  }

  @Test
  void fileFlushRoutesToFlushHandler() throws Exception {
    var routed = fileRouter.route(request("POST", "/api/dsl/files/flush"));

    assertThat(routed).isPresent();
    routed.get().handle(request("POST", "/api/dsl/files/flush"));
    verify(fileHandler).flush(any());
  }

  @Test
  void fileBulkRoutesToBulkHandler() throws Exception {
    var routed = fileRouter.route(request("POST", "/api/dsl/files/bulk"));

    assertThat(routed).isPresent();
    routed.get().handle(request("POST", "/api/dsl/files/bulk"));
    verify(fileHandler).bulkWrite(any());
  }

  @Test
  void fileWildcardStillMatchesNestedPaths() throws Exception {
    var routed = fileRouter.route(request("GET", "/api/dsl/files/src/dsl/A.java"));

    assertThat(routed).isPresent();
    routed.get().handle(request("GET", "/api/dsl/files/src/dsl/A.java"));
    verify(fileHandler).read(any());
  }

  @Test
  void docStatusRoutesToStatusHandler() throws Exception {
    var routed = docsRouter.route(request("GET", "/api/dsl/docs/status"));

    assertThat(routed).isPresent();
    routed.get().handle(request("GET", "/api/dsl/docs/status"));
    verify(docsHandler).status(any());
    verify(docsHandler, never()).read(any());
  }

  @Test
  void docFlushRoutesToFlushHandler() throws Exception {
    var routed = docsRouter.route(request("POST", "/api/dsl/docs/flush"));

    assertThat(routed).isPresent();
    routed.get().handle(request("POST", "/api/dsl/docs/flush"));
    verify(docsHandler).flush(any());
  }

  private static ServerResponse ok(Object body) {
    return ServerResponse.ok().body(body);
  }

  private static ServerRequest request(String method, String path) {
    return ServerRequest.create(new MockHttpServletRequest(method, path), CONVERTERS);
  }
}
