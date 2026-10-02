package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.controller.DslDocsHandler;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@ConditionalOnProperty(prefix = "cbs.dsl.docs", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslDocsRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/docs", beanClass = DslDocsHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDslDocs", summary = "List DSL doc files", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Doc entries"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/by-name/{name}", beanClass = DslDocsHandler.class, beanMethod = "readByName", method = RequestMethod.GET, operation = @Operation(operationId = "readDslDocByName", summary = "Read DSL doc markdown by construct name", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Doc content"),
              @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "No doc for this construct"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", description = "Bulkhead saturated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/by-name/{name}", beanClass = DslDocsHandler.class, beanMethod = "writeByName", method = RequestMethod.POST, operation = @Operation(operationId = "writeDslDocByName", summary = "Stage DSL doc markdown by construct name", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Write staged"),
              @ApiResponse(responseCode = "400", description = "Invalid name or body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/exists/{*path}", beanClass = DslDocsHandler.class, beanMethod = "exists", method = RequestMethod.GET, operation = @Operation(operationId = "dslDocExists", summary = "Check whether a DSL doc exists", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Existence flag"),
              @ApiResponse(responseCode = "400", description = "Invalid path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/{*path}", beanClass = DslDocsHandler.class, beanMethod = "read", method = RequestMethod.GET, operation = @Operation(operationId = "readDslDoc", summary = "Read DSL doc markdown", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Doc content"),
              @ApiResponse(responseCode = "400", description = "Invalid path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", description = "Bulkhead saturated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/bulk", beanClass = DslDocsHandler.class, beanMethod = "bulkWrite", method = RequestMethod.POST, operation = @Operation(operationId = "bulkWriteDslDocs", summary = "Stage many DSL doc writes", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Bulk writes staged"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/flush", beanClass = DslDocsHandler.class, beanMethod = "flush", method = RequestMethod.POST, operation = @Operation(operationId = "flushDslDocs", summary = "Flush staged DSL doc writes to disk", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Flush result"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/status", beanClass = DslDocsHandler.class, beanMethod = "status", method = RequestMethod.GET, operation = @Operation(operationId = "dslDocsStatus", summary = "Pending doc write count", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Pending count"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/docs/{*path}", beanClass = DslDocsHandler.class, beanMethod = "write", method = RequestMethod.POST, operation = @Operation(operationId = "writeDslDoc", summary = "Stage DSL doc markdown", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Write staged"),
              @ApiResponse(responseCode = "400", description = "Invalid path or body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslDocsRouter(DslDocsHandler docsHandler,
          RequestBodyLimitFilter requestBodyLimitFilter) {
    return RouterFunctions.route()
            .GET("/api/dsl/docs", docsHandler::list)
            .GET("/api/dsl/docs/by-name/{name}", docsHandler::readByName)
            .POST("/api/dsl/docs/by-name/{name}", docsHandler::writeByName)
            .POST("/api/dsl/docs/bulk", docsHandler::bulkWrite)
            .POST("/api/dsl/docs/flush", docsHandler::flush)
            .GET("/api/dsl/docs/status", docsHandler::status)
            .GET("/api/dsl/docs/exists/{*path}", docsHandler::exists)
            .GET("/api/dsl/docs/{*path}", docsHandler::read)
            .POST("/api/dsl/docs/{*path}", docsHandler::write)
            .filter(requestBodyLimitFilter)
            .build();
  }
}
