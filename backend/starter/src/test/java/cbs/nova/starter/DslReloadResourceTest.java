package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.config.DslReloadRouterConfiguration;
import cbs.nova.starter.controllers.DslReloadResource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.lang.reflect.Field;
import java.util.List;

class DslReloadResourceTest {

  private DslReloadResource resource;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    resource = new DslReloadResource();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  private void setSourceDir(String value) throws Exception {
    Field field = DslReloadResource.class.getDeclaredField("sourceDirProperty");
    field.setAccessible(true);
    field.set(resource, value);
  }

  private static ServerRequest reloadRequest() {
    return ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/reload"), List.of());
  }

  @Test
  void reloadReturns409WhenSourceDirBlank() throws Exception {
    setSourceDir("");
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void reloadReturns409WhenSourceDirNotFound() throws Exception {
    setSourceDir("/tmp/cbs-nova-does-not-exist-" + System.nanoTime());
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslReloadRouterConfiguration.class, DslReloadResource.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void routerFunctionSkippedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslReloadRouterConfiguration.class, DslReloadResource.class)
            .withPropertyValues("dsl.reload.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }
}
