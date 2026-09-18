package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.DslManifestConfiguration;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.config.router.DslManifestRouterConfiguration;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ManifestGuardEntry;
import cbs.nova.starter.model.ManifestReloadResponse;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PreCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.security.RoleResolver;
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

    DslManifestHandler handler = new DslManifestHandler(
            service, new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME));
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

  @Test
  void guardResolvesOnlyRoleChecksAndLeaksNoInternals() {
    PieceManifestService service = mock(PieceManifestService.class);
    when(service.byTarget(any(Target.class))).thenReturn(List.of(
            new Piece("workbench-publish", new Target.ButtonTarget("workbench-publish-btn"),
                    List.of(new PreCheck.RoleCheck(List.of("author", "admin"))),
                    List.of(), "deny"),
            new Piece("open-button", new Target.ButtonTarget("open-btn"),
                    List.of(), List.of(), "deny"),
            new Piece("flag-gated-button", new Target.ButtonTarget("flag-btn"),
                    List.of(new PreCheck.FeatureFlagCheck("some-internal-flag")),
                    List.of(), "deny")));

    DslManifestHandler handler = new DslManifestHandler(
            service, new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME));
    // No security context + no API key → anonymous resolves to VIEWER (T549 semantics).
    ServerRequest request = ServerRequest.create(
            new MockHttpServletRequest("GET", "/api/dsl/manifest/guard"), List.of());

    List<ManifestGuardEntry> entries = handler.resolveGuard(request);

    assertThat(entries).containsExactly(
            new ManifestGuardEntry("workbench-publish", false, "role"),
            new ManifestGuardEntry("open-button", true, null),
            // Feature-flag checks are enforcement-time concerns (T549) and do not gate this
            // snapshot read — the piece resolves allowed for the snapshot.
            new ManifestGuardEntry("flag-gated-button", true, null));
    // No check internals anywhere in the payload: no role names, no flag names.
    assertThat(entries.toString()).doesNotContain("author", "admin", "some-internal-flag");
  }

  @Test
  void guardResolvesApiKeyPrincipalToAdmin() {
    PieceManifestService service = mock(PieceManifestService.class);
    when(service.byTarget(any(Target.class))).thenReturn(List.of(
            new Piece("workbench-publish", new Target.ButtonTarget("workbench-publish-btn"),
                    List.of(new PreCheck.RoleCheck(List.of("author", "admin"))),
                    List.of(), "deny")));

    DslManifestHandler handler = new DslManifestHandler(
            service, new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME));
    MockHttpServletRequest servlet = new MockHttpServletRequest("GET", "/api/dsl/manifest/guard");
    servlet.addHeader(StarterConstants.API_KEY_HEADER, "secret");
    ServerRequest request = ServerRequest.create(servlet, List.of());

    assertThat(handler.resolveGuard(request))
            .containsExactly(new ManifestGuardEntry("workbench-publish", true, null));
  }
}
