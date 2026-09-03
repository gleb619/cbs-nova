package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.VcsModels.DefinitionBundle;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@ConditionalOnProperty(prefix = "cbs.dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslDefinitionBundleRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/definitions/export", beanClass = DslDraftHandler.class, beanMethod = "exportBundle", method = RequestMethod.GET, operation = @Operation(operationId = "exportDefinitions", summary = "Export published (and optionally draft) definition metadata as a bundle", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Bundle exported", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DefinitionBundle.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/definitions/import", beanClass = DslDraftHandler.class, beanMethod = "importBundle", method = RequestMethod.POST, operation = @Operation(operationId = "importDefinitions", summary = "Import a definition metadata bundle, snapshotting history and reloading DSL", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Bundle imported (reload may have failed; see response)", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImportBundleResult.class))),
              @ApiResponse(responseCode = "400", description = "Invalid bundle body or too many definitions", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslDefinitionBundleRouter(DslDraftHandler draftHandler) {
    return RouterFunctions.route()
            .GET("/api/dsl/definitions/export", draftHandler::exportBundle)
            .POST("/api/dsl/definitions/import", draftHandler::importBundle)
            .build();
  }

}
