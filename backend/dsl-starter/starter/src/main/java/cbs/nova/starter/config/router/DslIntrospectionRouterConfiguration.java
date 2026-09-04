package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.NamesResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDiagramDto;
import cbs.nova.starter.model.DslIntrospectionModels.TransactionDetail;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.web.bind.annotation.RequestMethod;

@Configuration
public class DslIntrospectionRouterConfiguration {

  @Bean
  DslIntrospectionHandler dslIntrospectionHandler(DslIntrospectionService service,
          ExplainDiagramRenderer diagramRenderer) {
    return new DslIntrospectionHandler(service, diagramRenderer);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/processes", beanClass = DslIntrospectionHandler.class, beanMethod = "processes", method = RequestMethod.GET, operation = @Operation(operationId = "listProcesses", summary = "List DSL process names", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", description = "Process names", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NamesResponse.class))))),
      @RouterOperation(path = "/api/dsl/processes/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "processDetail", method = RequestMethod.GET, operation = @Operation(operationId = "getProcessDetail", summary = "Get DSL process detail", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Process detail", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProcessDetail.class))))),
      @RouterOperation(path = "/api/dsl/processes/{name}/diagram", beanClass = DslIntrospectionHandler.class, beanMethod = "processDiagram", method = RequestMethod.GET, operation = @Operation(operationId = "getProcessDiagram", summary = "Get DSL process diagram", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Process diagram", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProcessDiagramDto.class))))),
      @RouterOperation(path = "/api/dsl/transactions", beanClass = DslIntrospectionHandler.class, beanMethod = "transactions", method = RequestMethod.GET, operation = @Operation(operationId = "listTransactions", summary = "List DSL transaction names", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", description = "Transaction names", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NamesResponse.class))))),
      @RouterOperation(path = "/api/dsl/transactions/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "transactionDetail", method = RequestMethod.GET, operation = @Operation(operationId = "getTransactionDetail", summary = "Get DSL transaction detail", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Transaction detail", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionDetail.class))))),
      @RouterOperation(path = "/api/dsl/objects/search", beanClass = DslIntrospectionHandler.class, beanMethod = "searchObjects", method = RequestMethod.GET, operation = @Operation(operationId = "searchObjects", summary = "Search DSL objects", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY),
              @Parameter(name = "description", in = ParameterIn.QUERY)
          }, responses = @ApiResponse(responseCode = "200", description = "Search results", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HelperSearchResult.class))))),
      @RouterOperation(path = "/api/dsl/helpers", beanClass = DslIntrospectionHandler.class, beanMethod = "helpers", method = RequestMethod.GET, operation = @Operation(operationId = "listHelpers", summary = "List DSL helpers", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", description = "Helper catalog", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HelperSearchResult.class))))),
      @RouterOperation(path = "/api/dsl/constructs/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "constructBody", method = RequestMethod.GET, operation = @Operation(operationId = "getConstructBody", summary = "Get DSL construct body", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Construct body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConstructBodyDto.class))))),
      @RouterOperation(path = "/api/dsl/definitions", beanClass = DslIntrospectionHandler.class, beanMethod = "definitions", method = RequestMethod.GET, operation = @Operation(operationId = "listDefinitions", summary = "List DSL definitions", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", description = "Definitions", content = @Content(mediaType = "application/json", schema = @Schema(implementation = DefinitionMetaDto.class))))),
      @RouterOperation(path = "/api/dsl/definitions/{name}/description", beanClass = DslIntrospectionHandler.class, beanMethod = "updateDescription", method = RequestMethod.PATCH, operation = @Operation(operationId = "updateDescription", summary = "Update DSL construct description", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Description updated"),
              @ApiResponse(responseCode = "400", description = "Invalid request"),
              @ApiResponse(responseCode = "404", description = "Construct not found")
          }))
  })
  public RouterFunction<ServerResponse> dslIntrospectionRouter(DslIntrospectionHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/processes", handler::processes)
            .GET("/api/dsl/processes/{name}", handler::processDetail)
            .GET("/api/dsl/processes/{name}/diagram", handler::processDiagram)
            .GET("/api/dsl/transactions", handler::transactions)
            .GET("/api/dsl/transactions/{name}", handler::transactionDetail)
            .GET("/api/dsl/objects/search", handler::searchObjects)
            .GET("/api/dsl/helpers", handler::helpers)
            .GET("/api/dsl/constructs/{name}", handler::constructBody)
            .GET("/api/dsl/definitions", handler::definitions)
            .PATCH("/api/dsl/definitions/{name}/description", handler::updateDescription)
            .build();
  }
}
