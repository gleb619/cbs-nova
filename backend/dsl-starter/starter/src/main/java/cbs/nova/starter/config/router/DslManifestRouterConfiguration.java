package cbs.nova.starter.config.router;

import cbs.nova.starter.config.DslManifestConfiguration;
import cbs.nova.starter.controller.DslManifestHandler;
import cbs.nova.starter.model.ManifestReloadResponse;
import cbs.nova.starter.service.PieceManifestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Registers the piece-manifest hot-reload route under {@code /api/dsl/manifest/reload}. The whole
 * router is conditional on a {@link PieceManifestService} bean so the route vanishes when the
 * manifest loader is disabled.
 */
@AutoConfiguration
@AutoConfigureAfter(DslManifestConfiguration.class)
@ConditionalOnBean(PieceManifestService.class)
public class DslManifestRouterConfiguration {

  @Bean
  DslManifestHandler dslManifestHandler(PieceManifestService pieceManifestService) {
    return new DslManifestHandler(pieceManifestService);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/manifest/reload", beanClass = DslManifestHandler.class, beanMethod = "reload", method = RequestMethod.POST, operation = @Operation(operationId = "reloadManifest", summary = "Reload and validate the piece manifest YAML", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Reload successful", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ManifestReloadResponse.class))),
              @ApiResponse(responseCode = "400", description = "Manifest validation failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ManifestReloadResponse.class))),
              @ApiResponse(responseCode = "409", description = "Manifest reload is disabled or not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.dsl.model.ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Unexpected reload failure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.dsl.model.ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslManifestRouter(DslManifestHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/manifest/reload", handler::reload)
            .build();
  }
}
