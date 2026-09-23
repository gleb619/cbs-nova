package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.NamesResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectStructureDto;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDiagramDto;
import cbs.nova.starter.model.DslIntrospectionModels.WorkingSetResponse;
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
          HierarchyDiagramRenderer diagramRenderer,
          RequestQueryConverter requestQueryConverter) {
    return new DslIntrospectionHandler(service, diagramRenderer, requestQueryConverter);
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
      @RouterOperation(path = "/api/dsl/working-set", beanClass = DslIntrospectionHandler.class, beanMethod = "workingSet", method = RequestMethod.GET, operation = @Operation(operationId = "getWorkingSet", summary = "Get DSL working set", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY),
              @Parameter(name = "description", in = ParameterIn.QUERY),
              @Parameter(name = "limit", in = ParameterIn.QUERY),
              @Parameter(name = "offset", in = ParameterIn.QUERY)
          }, responses = @ApiResponse(responseCode = "200", description = "Working set", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkingSetResponse.class))))),
      @RouterOperation(path = "/api/dsl/objects/search", beanClass = DslIntrospectionHandler.class, beanMethod = "searchObjects", method = RequestMethod.GET, operation = @Operation(operationId = "searchObjects", summary = "Search DSL objects", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY),
              @Parameter(name = "description", in = ParameterIn.QUERY)
          }, responses = @ApiResponse(responseCode = "200", description = "Search results", content = @Content(mediaType = "application/json", schema = @Schema(implementation = HelperSearchResult.class))))),
      @RouterOperation(path = "/api/dsl/helpers", beanClass = DslIntrospectionHandler.class, beanMethod = "helpers", method = RequestMethod.GET, operation = @Operation(operationId = "listHelpers", summary = "List DSL helpers", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum helpers to return (default 100)"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of helpers to skip"),
              @Parameter(name = "search", in = ParameterIn.QUERY, description = "Text to search"),
              @Parameter(name = "searchMode", in = ParameterIn.QUERY, description = "Search mode: exact (default), cosine, fuzzy")
          }, responses = @ApiResponse(responseCode = "200", description = "Paged helper catalog", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/dsl/constructs/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "constructBody", method = RequestMethod.GET, operation = @Operation(operationId = "getConstructBody", summary = "Get DSL construct body", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Construct body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConstructBodyDto.class))))),
      @RouterOperation(path = "/api/dsl/schemas/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "constructSchema", method = RequestMethod.GET, operation = @Operation(operationId = "getConstructSchema", summary = "Get DSL construct input/output schemas (preview) or explain report (mode=explain)", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.PATH),
              @Parameter(name = "mode", in = ParameterIn.QUERY, description = "Schema mode: preview (default) or explain")
          }, responses = @ApiResponse(responseCode = "200", description = "Construct schemas or explain report", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConstructSchemaDto.class))))),
      @RouterOperation(path = "/api/dsl/structures/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "objectStructure", method = RequestMethod.GET, operation = @Operation(operationId = "getObjectStructure", summary = "Get DSL object structure", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", description = "Object structure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectStructureDto.class))))),
      @RouterOperation(path = "/api/dsl/definitions", beanClass = DslIntrospectionHandler.class, beanMethod = "definitions", method = RequestMethod.GET, operation = @Operation(operationId = "listDefinitions", summary = "List DSL definitions", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", description = "Definitions", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))))
  })
  public RouterFunction<ServerResponse> dslIntrospectionRouter(DslIntrospectionHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/processes", handler::processes)
            .GET("/api/dsl/processes/{name}", handler::processDetail)
            .GET("/api/dsl/processes/{name}/diagram", handler::processDiagram)
            .GET("/api/dsl/transactions", handler::transactions)
            .GET("/api/dsl/transactions/{name}", handler::transactionDetail)
            .GET("/api/dsl/working-set", handler::workingSet)
            .GET("/api/dsl/objects/search", handler::searchObjects)
            .GET("/api/dsl/helpers", handler::helpers)
            .GET("/api/dsl/constructs/{name}", handler::constructBody)
            .GET("/api/dsl/schemas/{name}", handler::constructSchema)
            .GET("/api/dsl/structures/{name}", handler::objectStructure)
            .GET("/api/dsl/definitions", handler::definitions)
            .build();
  }
}
