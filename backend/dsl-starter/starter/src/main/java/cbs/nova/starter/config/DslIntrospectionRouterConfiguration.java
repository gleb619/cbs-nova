package cbs.nova.starter.config;

import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.NamesResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDetail;
import cbs.nova.starter.model.DslIntrospectionModels.TransactionDetail;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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

@Configuration
public class DslIntrospectionRouterConfiguration {

  @Bean
  DslIntrospectionHandler dslIntrospectionHandler(DslIntrospectionService service) {
    return new DslIntrospectionHandler(service);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/processes", beanClass = DslIntrospectionHandler.class, beanMethod = "processes", method = RequestMethod.GET, operation = @Operation(operationId = "listProcesses", summary = "List all registered DSL process names", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NamesResponse.class))))),
      @RouterOperation(path = "/api/dsl/processes/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "processDetail", method = RequestMethod.GET, operation = @Operation(operationId = "getProcess", summary = "Get metadata of a single DSL process", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProcessDetail.class))))),
      @RouterOperation(path = "/api/dsl/transactions", beanClass = DslIntrospectionHandler.class, beanMethod = "transactions", method = RequestMethod.GET, operation = @Operation(operationId = "listTransactions", summary = "List all registered DSL transaction names", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NamesResponse.class))))),
      @RouterOperation(path = "/api/dsl/transactions/{name}", beanClass = DslIntrospectionHandler.class, beanMethod = "transactionDetail", method = RequestMethod.GET, operation = @Operation(operationId = "getTransaction", summary = "Get metadata of a single DSL transaction", tags = {
          "DSL Introspection"}, parameters = @Parameter(name = "name", in = ParameterIn.PATH), responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = TransactionDetail.class))))),
      @RouterOperation(path = "/api/dsl/objects/search", beanClass = DslIntrospectionHandler.class, beanMethod = "searchObjects", method = RequestMethod.GET, operation = @Operation(operationId = "searchObjects", summary = "Search registered DSL helpers, processes, transactions and functions", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "name", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY),
              @Parameter(name = "description", in = ParameterIn.QUERY)
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = HelperSearchResult.class)))))),
      @RouterOperation(path = "/api/dsl/helpers", beanClass = DslIntrospectionHandler.class, beanMethod = "helpers", method = RequestMethod.GET, operation = @Operation(operationId = "listHelpers", summary = "List all registered DSL helper names", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = NamesResponse.class))))),
      @RouterOperation(path = "/api/dsl/definitions", beanClass = DslIntrospectionHandler.class, beanMethod = "definitions", method = RequestMethod.GET, operation = @Operation(operationId = "listDefinitions", summary = "List all registered DSL definitions", tags = {
          "DSL Introspection"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = DefinitionMetaDto.class))))))
  })
  public RouterFunction<ServerResponse> dslIntrospectionRouter(DslIntrospectionHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/processes", handler::processes)
            .GET("/api/dsl/processes/{name}", handler::processDetail)
            .GET("/api/dsl/transactions", handler::transactions)
            .GET("/api/dsl/transactions/{name}", handler::transactionDetail)
            .GET("/api/dsl/objects/search", handler::searchObjects)
            .GET("/api/dsl/helpers", handler::helpers)
            .GET("/api/dsl/definitions", handler::definitions)
            .build();
  }
}
