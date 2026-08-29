package cbs.nova.starter.config;

import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.controller.DslRuntimeHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
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
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DslRuntimeRouterConfiguration {

  @Bean
  DslPayloadSizeValidator dslPayloadSizeValidator(
          ObjectMapper objectMapper,
          DslRunsProperties properties) {
    return new DslPayloadSizeValidator(objectMapper, properties);
  }

  @Bean
  DslRuntimeHandler dslRuntimeHandler(
          DslRuntimeService service,
          DslPayloadSizeValidator payloadSizeValidator) {
    return new DslRuntimeHandler(service, payloadSizeValidator);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/preview/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "preview", method = RequestMethod.POST, operation = @Operation(operationId = "previewDsl", summary = "Preview a DSL process without side effects", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Dry-run preview report including AST, execution trace, and captured external calls", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PreviewReport.class))))),
      @RouterOperation(path = "/api/dsl/run/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "run", method = RequestMethod.POST, operation = @Operation(operationId = "runDsl", summary = "Execute a DSL process with full side effects", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Execution result", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
              @ApiResponse(responseCode = "422", description = "Execution failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/explain/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "explain", method = RequestMethod.POST, operation = @Operation(operationId = "explainDsl", summary = "Return a static analysis report of a DSL process", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Static analysis report including diagrams, AST, and dry-run logs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExplainReport.class)))))
  })
  public RouterFunction<ServerResponse> dslRuntimeRouter(DslRuntimeHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/preview/{name}", handler::preview)
            .POST("/api/dsl/run/{name}", handler::run)
            .POST("/api/dsl/explain/{name}", handler::explain)
            .build();
  }
}
