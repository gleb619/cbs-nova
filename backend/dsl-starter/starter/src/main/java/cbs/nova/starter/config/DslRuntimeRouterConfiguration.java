package cbs.nova.starter.config;

import cbs.nova.dsl.ExplainReport;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.config.properties.InputValidationProperties;
import cbs.nova.starter.controller.DslRuntimeHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ValidationErrorsResponse;
import cbs.nova.starter.service.DslRuntimeService;
import cbs.nova.starter.service.InputValidator;
import cbs.nova.starter.web.DslPayloadSizeValidator;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

@Configuration
@EnableConfigurationProperties(InputValidationProperties.class)
public class DslRuntimeRouterConfiguration {

  @Bean
  DslPayloadSizeValidator dslPayloadSizeValidator(
          ObjectMapper objectMapper,
          DslRunsProperties properties) {
    return new DslPayloadSizeValidator(objectMapper, properties);
  }

  @Bean
  InputValidator inputValidator(
          JsonSchemaGenerator schemaGenerator,
          InputValidationProperties properties,
          CbsNovaCacheProperties cacheProperties) {
    var spec = cacheProperties.specFor(CbsNovaCacheProperties.Names.INPUT_SCHEMA);
    Cache<String, Map<String, Object>> cache = Caffeine.newBuilder()
            .expireAfterWrite(spec.ttl())
            .maximumSize(spec.maxSize())
            .build();
    return new InputValidator(schemaGenerator, properties, cache);
  }

  @Bean
  DslRuntimeHandler dslRuntimeHandler(
          DslRuntimeService service,
          DslPayloadSizeValidator payloadSizeValidator,
          InputValidator inputValidator) {
    return new DslRuntimeHandler(service, payloadSizeValidator, inputValidator);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/preview/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "preview", method = RequestMethod.POST, operation = @Operation(operationId = "previewDsl", summary = "Preview a DSL process without side effects", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Dry-run preview report including AST, execution trace, and captured external calls", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PreviewReport.class))),
              @ApiResponse(responseCode = "422", description = "Input validation or execution failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/run/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "run", method = RequestMethod.POST, operation = @Operation(operationId = "runDsl", summary = "Execute a DSL process with full side effects", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Execution result", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Object.class))),
              @ApiResponse(responseCode = "422", description = "Input validation or execution failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/explain/{name}", beanClass = DslRuntimeHandler.class, beanMethod = "explain", method = RequestMethod.POST, operation = @Operation(operationId = "explainDsl", summary = "Return a static analysis report of a DSL process", tags = {
          "DSL Runtime"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Static analysis report including diagrams, AST, and dry-run logs", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExplainReport.class))),
              @ApiResponse(responseCode = "422", description = "Input validation failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ValidationErrorsResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslRuntimeRouter(DslRuntimeHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/preview/{name}", handler::preview)
            .POST("/api/dsl/run/{name}", handler::run)
            .POST("/api/dsl/explain/{name}", handler::explain)
            .build();
  }
}
