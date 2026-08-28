package cbs.nova.starter.config;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.starter.controller.DslExecutionsHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ExecutionDto;
import cbs.nova.starter.model.ExecutionListResponse;
import cbs.nova.starter.service.DslRunCancellationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DslExecutionsRouterConfiguration {

  @Bean
  DslExecutionsHandler dslExecutionsHandler(DslRunRepository runRepository,
          ObjectMapper objectMapper,
          DslRunCancellationService dslRunCancellationService) {
    return new DslExecutionsHandler(runRepository, objectMapper, dslRunCancellationService);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/executions", beanClass = DslExecutionsHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listExecutions", summary = "List DSL execution runs", tags = {
          "DSL Executions"}, parameters = {
              @Parameter(name = "processName", in = ParameterIn.QUERY),
              @Parameter(name = "status", in = ParameterIn.QUERY),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of runs to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching runs to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionListResponse.class))))),
      @RouterOperation(path = "/api/executions/{id}", beanClass = DslExecutionsHandler.class, beanMethod = "detail", method = RequestMethod.GET, operation = @Operation(operationId = "getExecution", summary = "Get a single DSL execution run by id", tags = {
          "DSL Executions"}, parameters = @Parameter(name = "id", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "The execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class))),
              @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/executions/{id}/cancel", beanClass = DslExecutionsHandler.class, beanMethod = "cancel", method = RequestMethod.POST, operation = @Operation(operationId = "cancelExecution", summary = "Cancel a running DSL execution run", tags = {
          "DSL Executions"}, parameters = @Parameter(name = "id", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "The cancelled execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class))),
              @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "The run is not in a cancellable state", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslExecutionsRouter(DslExecutionsHandler handler) {
    return RouterFunctions.route()
            .GET("/api/executions", handler::list)
            .GET("/api/executions/{id}", handler::detail)
            .POST("/api/executions/{id}/cancel", handler::cancel)
            .build();
  }
}
