package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslFileHandler;
import cbs.nova.starter.model.ErrorResponse;
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
@ConditionalOnProperty(prefix = "cbs.dsl.files", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslFileRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/files", beanClass = DslFileHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDslFiles", summary = "List DSL source files", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "File entries"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/by-name/{name}", beanClass = DslFileHandler.class, beanMethod = "readByName", method = RequestMethod.GET, operation = @Operation(operationId = "readDslFileByName", summary = "Read DSL source file content by construct name", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "File content"),
              @ApiResponse(responseCode = "400", description = "Invalid name", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Construct not found or has no source file"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", description = "Bulkhead saturated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/by-name/{name}", beanClass = DslFileHandler.class, beanMethod = "writeByName", method = RequestMethod.POST, operation = @Operation(operationId = "writeDslFileByName", summary = "Stage DSL source file content by construct name", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Write staged"),
              @ApiResponse(responseCode = "400", description = "Invalid name or body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Construct not found or has no source file"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/{*path}", beanClass = DslFileHandler.class, beanMethod = "read", method = RequestMethod.GET, operation = @Operation(operationId = "readDslFile", summary = "Read DSL source file content", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "File content"),
              @ApiResponse(responseCode = "400", description = "Invalid path", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", description = "Bulkhead saturated", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/bulk", beanClass = DslFileHandler.class, beanMethod = "bulkWrite", method = RequestMethod.POST, operation = @Operation(operationId = "bulkWriteDslFiles", summary = "Stage many DSL file writes", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Bulk writes staged"),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/flush", beanClass = DslFileHandler.class, beanMethod = "flush", method = RequestMethod.POST, operation = @Operation(operationId = "flushDslFiles", summary = "Flush staged DSL file writes to disk", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Flush result"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/status", beanClass = DslFileHandler.class, beanMethod = "status", method = RequestMethod.GET, operation = @Operation(operationId = "dslFileStatus", summary = "Pending write count", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Pending count"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/files/{*path}", beanClass = DslFileHandler.class, beanMethod = "write", method = RequestMethod.POST, operation = @Operation(operationId = "writeDslFile", summary = "Stage DSL source file content", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "202", description = "Write staged"),
              @ApiResponse(responseCode = "400", description = "Invalid path or body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Source directory not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslFileRouter(DslFileHandler fileHandler) {
    return RouterFunctions.route()
            .GET("/api/dsl/files", fileHandler::list)
            .GET("/api/dsl/files/by-name/{name}", fileHandler::readByName)
            .POST("/api/dsl/files/by-name/{name}", fileHandler::writeByName)
            .GET("/api/dsl/files/{*path}", fileHandler::read)
            .POST("/api/dsl/files/bulk", fileHandler::bulkWrite)
            .POST("/api/dsl/files/flush", fileHandler::flush)
            .GET("/api/dsl/files/status", fileHandler::status)
            .POST("/api/dsl/files/{*path}", fileHandler::write)
            .build();
  }
}
