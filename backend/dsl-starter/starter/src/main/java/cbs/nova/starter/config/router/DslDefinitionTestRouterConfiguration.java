package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslDefinitionTestHandler;
import cbs.nova.starter.model.DefinitionTestCase;
import cbs.nova.starter.model.DefinitionTestRunReport;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslDefinitionTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.List;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class DslDefinitionTestRouterConfiguration {

  @Bean
  DslDefinitionTestHandler dslDefinitionTestHandler(
          DslDefinitionTestService service,
          ObjectProvider<DslAuditService> auditServiceProvider) {
    return new DslDefinitionTestHandler(service, auditServiceProvider);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/definitions/{name}/tests", beanClass = DslDefinitionTestHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDefinitionTests", summary = "List stored test cases for a definition", tags = {
          "DSL Testing"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DefinitionTestCase.class))))),
      @RouterOperation(path = "/api/dsl/definitions/{name}/tests", beanClass = DslDefinitionTestHandler.class, beanMethod = "replace", method = RequestMethod.PUT, operation = @Operation(operationId = "replaceDefinitionTests", summary = "Replace the whole test case set for a definition", tags = {
          "DSL Testing"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Replaced case set stored", content = @Content(mediaType = "application/json", schema = @Schema(implementation = List.class))),
              @ApiResponse(responseCode = "404", description = "Definition not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})),
      @RouterOperation(path = "/api/dsl/definitions/{name}/tests/run", beanClass = DslDefinitionTestHandler.class, beanMethod = "run", method = RequestMethod.POST, operation = @Operation(operationId = "runDefinitionTests", summary = "Run a definition's test cases through the preview pipeline", tags = {
          "DSL Testing"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.PATH),
              @Parameter(name = "case", in = ParameterIn.QUERY, description = "Optional case-name subset; may be repeated")}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DefinitionTestRunReport.class)))))
  })
  public RouterFunction<ServerResponse> dslDefinitionTestRouter(
          DslDefinitionTestHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/definitions/{name}/tests", handler::list)
            .PUT("/api/dsl/definitions/{name}/tests", handler::replace)
            .POST("/api/dsl/definitions/{name}/tests/run", handler::run)
            .build();
  }
}
