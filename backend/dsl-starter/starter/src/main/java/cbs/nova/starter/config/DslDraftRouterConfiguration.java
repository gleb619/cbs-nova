package cbs.nova.starter.config;

import cbs.nova.starter.controllers.DslDraftHandler;
import cbs.nova.starter.models.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@AutoConfiguration
@ConditionalOnProperty(prefix = "dsl.drafts", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslDraftRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/drafts/{name}/save", beanClass = DslDraftHandler.class, beanMethod = "save", method = RequestMethod.POST, operation = @Operation(operationId = "saveDraft", summary = "Persist a Workbench draft construct to disk", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Draft saved"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/publish", beanClass = DslDraftHandler.class, beanMethod = "publish", method = RequestMethod.POST, operation = @Operation(operationId = "publishDraft", summary = "Persist a Workbench construct as published and reload DSL", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Construct published"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Reload failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslDraftRouter(DslDraftHandler draftHandler) {
    return RouterFunctions.route()
            .POST("/api/dsl/drafts/{name}/save", draftHandler::save)
            .POST("/api/dsl/drafts/{name}/publish", draftHandler::publish)
            .build();
  }
}