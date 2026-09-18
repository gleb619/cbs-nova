package cbs.nova.starter.config.router;

import cbs.nova.starter.config.DslManifestConfiguration;
import cbs.nova.starter.controller.DslManifestHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ManifestGuardEntry;
import cbs.nova.starter.model.ManifestReloadResponse;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.PieceManifestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Registers the piece-manifest routes: the hot-reload endpoint under
 * {@code /api/dsl/manifest/reload} (T548) and the read-only button-guard snapshot under
 * {@code /api/dsl/manifest/guard} (T551). The whole router is conditional on a
 * {@link PieceManifestService} bean so the routes vanish when the manifest loader is disabled.
 */
@AutoConfiguration
@AutoConfigureAfter(DslManifestConfiguration.class)
@ConditionalOnBean(PieceManifestService.class)
public class DslManifestRouterConfiguration {

  @Bean
  DslManifestHandler dslManifestHandler(PieceManifestService pieceManifestService,
          ObjectProvider<RoleResolver> roleResolverProvider) {
    // Same shared RoleResolver PieceGuardFilter/RbacAuthorizationFilter use (resolved lazily so
    // minimal test contexts without a RoleResolver bean still boot). In practice the
    // PieceGuardFilterConfiguration / RbacFilterConfiguration bean always wins; the fallback
    // only mirrors their default-claim behaviour for bare contexts.
    RoleResolver roleResolver = roleResolverProvider.getIfAvailable(
            () -> new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME));
    return new DslManifestHandler(pieceManifestService, roleResolver);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/manifest/reload", beanClass = DslManifestHandler.class, beanMethod = "reload", method = RequestMethod.POST, operation = @Operation(operationId = "reloadManifest", summary = "Reload and validate the piece manifest YAML", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Reload successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ManifestReloadResponse.class))),
              @ApiResponse(responseCode = "400", description = "Manifest validation failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ManifestReloadResponse.class))),
              @ApiResponse(responseCode = "409", description = "Manifest reload is disabled or not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.dsl.model.ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Unexpected reload failure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.dsl.model.ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/manifest/guard", beanClass = DslManifestHandler.class, beanMethod = "guard", method = RequestMethod.GET, operation = @Operation(operationId = "getManifestGuard", summary = "Button-target piece guard verdicts resolved for the current principal (no check internals)", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Per-piece allow/deny verdicts for button targets", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = ManifestGuardEntry.class)))),
              @ApiResponse(responseCode = "500", description = "Unexpected resolution failure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.dsl.model.ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslManifestRouter(DslManifestHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/manifest/reload", handler::reload)
            .GET("/api/dsl/manifest/guard", handler::guard)
            .build();
  }
}
