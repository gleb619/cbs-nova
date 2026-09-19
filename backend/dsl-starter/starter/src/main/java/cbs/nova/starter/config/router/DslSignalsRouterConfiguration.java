package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.controller.DslSignalsHandler;
import cbs.nova.starter.service.DslSignalService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DslSignalsRouterConfiguration {

  @Bean
  DslSignalsHandler dslSignalsHandler(DslSignalService signalService, ObjectMapper objectMapper) {
    return new DslSignalsHandler(signalService, objectMapper);
  }

  @Bean

  @RouterOperations({
      @RouterOperation(path = "/api/dsl/signals/{runId}", beanClass = DslSignalsHandler.class, beanMethod = "sendSignal", method = RequestMethod.POST, operation = @Operation(operationId = "sendSignal", summary = "Send a signal to a running DSL process", tags = {
          "DSL Signals"}, parameters = @Parameter(name = "runId", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Signal sent"),
              @ApiResponse(responseCode = "404", description = "Run not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Run is not running", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/queries/{runId}", beanClass = DslSignalsHandler.class, beanMethod = "querySignalState", method = RequestMethod.GET, operation = @Operation(operationId = "querySignalState", summary = "Query current signal state of a running DSL process", tags = {
          "DSL Signals"}, parameters = @Parameter(name = "runId", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Signal state"),
              @ApiResponse(responseCode = "404", description = "Run not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslSignalsRouter(DslSignalsHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/signals/{runId}", handler::sendSignal)
            .GET("/api/dsl/queries/{runId}", handler::querySignalState)
            .build();
  }
}
