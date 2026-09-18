package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.DslManifestConfiguration;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.config.router.DslManifestRouterConfiguration;
import cbs.nova.starter.model.ManifestReloadResponse;
import cbs.nova.starter.service.PieceManifestService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

class DslManifestHandlerTest {

  @Test
  void reloadDelegatesToService() {
    PieceManifestService service = mock(PieceManifestService.class);
    when(service.reload(any(ServerRequest.class))).thenReturn(
            ServerResponse.ok()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new ManifestReloadResponse(7, List.of())));

    DslManifestHandler handler = new DslManifestHandler(service);
    ServerRequest request = ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/manifest/reload"), List.of());

    ServerResponse response = handler.reload(request);
    assertThat(response.statusCode().value()).isEqualTo(200);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslManifestConfiguration.class,
                    DslManifestRouterConfiguration.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void routerFunctionSkippedWhenManifestDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslManifestConfiguration.class,
                    DslManifestRouterConfiguration.class)
            .withPropertyValues("cbs.dsl.manifest.enabled=false")
            .run(ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).doesNotHaveBean(RouterFunction.class);
            });
  }

  @Test
  void propertiesPathCanBeOverridden() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslManifestConfiguration.class)
            .withPropertyValues("cbs.dsl.manifest.path=classpath:missing-manifest.yaml")
            .run(ctx -> {
              assertThat(ctx).hasNotFailed();
              assertThat(ctx).hasSingleBean(PieceManifestService.class);
              assertThat(ctx).hasSingleBean(CbsDslManifestProperties.class);
            });
  }
}
