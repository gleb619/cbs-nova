package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.model.ErrorResponse;
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
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history", beanClass = DslDraftHandler.class, beanMethod = "history", method = RequestMethod.GET, operation = @Operation(operationId = "listPublishHistory", summary = "List published metadata snapshots for a construct", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "List of history entries"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history/{timestamp}/restore", beanClass = DslDraftHandler.class, beanMethod = "restore", method = RequestMethod.POST, operation = @Operation(operationId = "restorePublishHistory", summary = "Restore a published metadata snapshot and reload DSL", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Snapshot restored"),
              @ApiResponse(responseCode = "404", description = "History entry not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Reload failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}", beanClass = DslDraftHandler.class, beanMethod = "delete", method = RequestMethod.DELETE, operation = @Operation(operationId = "deleteDraft", summary = "Delete a Workbench draft construct", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Draft deleted"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Draft not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts", beanClass = DslDraftHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDrafts", summary = "List Workbench draft summaries from .workbench/drafts", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "List of draft summaries")
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}", beanClass = DslDraftHandler.class, beanMethod = "read", method = RequestMethod.GET, operation = @Operation(operationId = "readDraft", summary = "Read a single Workbench draft payload", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Draft payload returned"),
              @ApiResponse(responseCode = "404", description = "Draft not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslDraftRouter(DslDraftHandler draftHandler) {
    return RouterFunctions.route()
            .GET("/api/dsl/drafts", draftHandler::list)
            .GET("/api/dsl/drafts/{name}", draftHandler::read)
            .POST("/api/dsl/drafts/{name}/save", draftHandler::save)
            .POST("/api/dsl/drafts/{name}/publish", draftHandler::publish)
            .GET("/api/dsl/drafts/{name}/history", draftHandler::history)
            .POST("/api/dsl/drafts/{name}/history/{timestamp}/restore", draftHandler::restore)
            .DELETE("/api/dsl/drafts/{name}", draftHandler::delete)
            .build();
  }
}
