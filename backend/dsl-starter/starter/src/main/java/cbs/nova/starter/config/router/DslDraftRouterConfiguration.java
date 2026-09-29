package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslDraftHandler;
import cbs.nova.starter.controller.DslDraftHistoryHandler;
import cbs.nova.starter.controller.DslDraftVcsHandler;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.VcsModels.CommitResult;
import cbs.nova.starter.model.VcsModels.FetchResult;
import cbs.nova.starter.model.VcsModels.PullResult;
import cbs.nova.starter.model.VcsModels.RebaseResult;
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
      @RouterOperation(path = "/api/dsl/drafts/{name}/save", beanClass = DslDraftHandler.class, beanMethod = "save", method = RequestMethod.POST, operation = @Operation(operationId = "saveDraft", summary = "Write DSL source for a definition to the source file", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Source saved"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/publish", beanClass = DslDraftHandler.class, beanMethod = "publish", method = RequestMethod.POST, operation = @Operation(operationId = "publishDraft", summary = "Write supplied source, reload DSL and commit the source file", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Construct published"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found, or git requires builder", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Reload failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history", beanClass = DslDraftHistoryHandler.class, beanMethod = "history", method = RequestMethod.GET, operation = @Operation(operationId = "listPublishHistory", summary = "List git commits for a definition's source path", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "List of history entries"),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or git requires builder", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history/{timestamp}", beanClass = DslDraftHistoryHandler.class, beanMethod = "historyEntry", method = RequestMethod.GET, operation = @Operation(operationId = "readPublishHistoryEntry", summary = "Read the DSL source file at a git commit", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "History entry content"),
              @ApiResponse(responseCode = "404", description = "History entry not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or git requires builder", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history/{timestamp}/diff", beanClass = DslDraftHistoryHandler.class, beanMethod = "historyDiff", method = RequestMethod.GET, operation = @Operation(operationId = "diffPublishHistoryEntry", summary = "Diff a git commit against the current working-tree source", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Line-level diff of entry vs current"),
              @ApiResponse(responseCode = "404", description = "History entry not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or git requires builder", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/history/{timestamp}/restore", beanClass = DslDraftHistoryHandler.class, beanMethod = "restore", method = RequestMethod.POST, operation = @Operation(operationId = "restorePublishHistory", summary = "Restore a git commit's source into the working tree", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Snapshot restored"),
              @ApiResponse(responseCode = "404", description = "History entry not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or git requires builder", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/discard", beanClass = DslDraftHandler.class, beanMethod = "discard", method = RequestMethod.POST, operation = @Operation(operationId = "discardDraft", summary = "Discard working-tree changes for a definition", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Changes discarded"),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available or source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}/commits", beanClass = DslDraftHistoryHandler.class, beanMethod = "commits", method = RequestMethod.GET, operation = @Operation(operationId = "listDraftCommits", summary = "List git commits for a definition's source path", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "List of commit entries"),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available or source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}", beanClass = DslDraftHandler.class, beanMethod = "delete", method = RequestMethod.DELETE, operation = @Operation(operationId = "deleteDraft", summary = "Discard working-tree changes for a definition", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Changes discarded"),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available or source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/metadata", beanClass = DslDraftHandler.class, beanMethod = "metadata", method = RequestMethod.GET, operation = @Operation(operationId = "getDraftsMetadata", summary = "Return workspace setup metadata for the drafts folder: number of git-changed source files, total size, current branch, and git/history config", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Drafts metadata returned")
          })),
      @RouterOperation(path = "/api/dsl/drafts", beanClass = DslDraftHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDrafts", summary = "List definitions whose source file has a git change", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "List of draft summaries", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/{name}", beanClass = DslDraftHandler.class, beanMethod = "read", method = RequestMethod.GET, operation = @Operation(operationId = "readDraft", summary = "Read the current DSL source file for a definition", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Draft payload returned"),
              @ApiResponse(responseCode = "404", description = "Definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/fetch", beanClass = DslDraftVcsHandler.class, beanMethod = "fetch", method = RequestMethod.POST, operation = @Operation(operationId = "fetchDraftsRepo", summary = "Fetch the configured git remote for the drafts workspace branch", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Fetch result with ahead/behind counts", content = @Content(mediaType = "application/json", schema = @Schema(implementation = FetchResult.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available, source directory not configured, or git remote missing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/pull", beanClass = DslDraftVcsHandler.class, beanMethod = "pull", method = RequestMethod.POST, operation = @Operation(operationId = "pullDraftsRepo", summary = "Pull the drafts workspace branch per the builder pull strategy", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Pull result; message carries conflict/abort details", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PullResult.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available, source directory not configured, or git remote missing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/rebase", beanClass = DslDraftVcsHandler.class, beanMethod = "rebase", method = RequestMethod.POST, operation = @Operation(operationId = "rebaseDraftsRepo", summary = "Rebase the drafts workspace branch onto its upstream", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Rebase result; message carries conflict/abort details", content = @Content(mediaType = "application/json", schema = @Schema(implementation = RebaseResult.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available, source directory not configured, or git remote missing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/drafts/commit", beanClass = DslDraftVcsHandler.class, beanMethod = "commit", method = RequestMethod.POST, operation = @Operation(operationId = "commitDrafts", summary = "Commit the listed definitions' source paths as one atomic commit", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Batch commit result", content = @Content(mediaType = "application/json", schema = @Schema(implementation = CommitResult.class))),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "A listed definition has no source path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Builder not available, source directory not configured, or nothing to commit", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslDraftRouter(DslDraftHandler draftHandler,
          DslDraftHistoryHandler historyHandler,
          DslDraftVcsHandler vcsHandler) {
    return RouterFunctions.route()
            .POST("/api/dsl/drafts/fetch", vcsHandler::fetch)
            .POST("/api/dsl/drafts/pull", vcsHandler::pull)
            .POST("/api/dsl/drafts/rebase", vcsHandler::rebase)
            .POST("/api/dsl/drafts/commit", vcsHandler::commit)
            .GET("/api/dsl/drafts/metadata", draftHandler::metadata)
            .GET("/api/dsl/drafts", draftHandler::list)
            .GET("/api/dsl/drafts/{name}", draftHandler::read)
            .POST("/api/dsl/drafts/{name}/save", draftHandler::save)
            .POST("/api/dsl/drafts/{name}/publish", draftHandler::publish)
            .GET("/api/dsl/drafts/{name}/history", historyHandler::history)
            .GET("/api/dsl/drafts/{name}/history/{timestamp}", historyHandler::historyEntry)
            .GET("/api/dsl/drafts/{name}/history/{timestamp}/diff", historyHandler::historyDiff)
            .POST("/api/dsl/drafts/{name}/history/{timestamp}/restore", historyHandler::restore)
            .POST("/api/dsl/drafts/{name}/discard", draftHandler::discard)
            .GET("/api/dsl/drafts/{name}/commits", historyHandler::commits)
            .DELETE("/api/dsl/drafts/{name}", draftHandler::delete)
            .build();
  }
}
