package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.controller.DslDefinitionSnapshotHandler;
import cbs.nova.starter.model.DefinitionSnapshotDto;
import cbs.nova.starter.model.DefinitionVersionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class DslDefinitionSnapshotRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/definitions/{name}/versions", beanClass = DslDefinitionSnapshotHandler.class, beanMethod = "versions", method = RequestMethod.GET, operation = @Operation(operationId = "listDefinitionVersions", summary = "List captured source snapshots of a definition, newest first", tags = {
          "DSL Definitions"}, responses = {
              @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DefinitionVersionDto.class)))),
              @ApiResponse(responseCode = "404", description = "Unknown definition", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})),
      @RouterOperation(path = "/api/dsl/definitions/{name}/versions/{hash}", beanClass = DslDefinitionSnapshotHandler.class, beanMethod = "version", method = RequestMethod.GET, operation = @Operation(operationId = "getDefinitionVersion", summary = "Get a definition source snapshot by content hash with a unified diff against the previous snapshot", tags = {
          "DSL Definitions"}, responses = {
              @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DefinitionSnapshotDto.class))),
              @ApiResponse(responseCode = "404", description = "Unknown definition or hash", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))}))
  })
  public RouterFunction<ServerResponse> dslDefinitionSnapshotRouter(
          DslDefinitionSnapshotHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/definitions/{name}/versions", handler::versions)
            .GET("/api/dsl/definitions/{name}/versions/{hash}", handler::version)
            .build();
  }
}
