package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslIntrospectionHandler;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectStructureDto;
import cbs.nova.starter.model.DslIntrospectionModels.WorkingSetResponse;
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
          RequestQueryConverter requestQueryConverter) {
    return new DslIntrospectionHandler(service, requestQueryConverter);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/working-set", beanClass = DslIntrospectionHandler.class, beanMethod = "workingSet", method = RequestMethod.GET, operation = @Operation(operationId = "getWorkingSet", summary = "Get DSL working set", tags = {
          "DSL Introspection"}, parameters = {@Parameter(name = "page", in = ParameterIn.QUERY),
              @Parameter(name = "size", in = ParameterIn.QUERY),
              @Parameter(name = "query", in = ParameterIn.QUERY),
              @Parameter(name = "mode", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY)}, responses = @ApiResponse(responseCode = "200", description = "Working set", content = @Content(mediaType = "application/json", schema = @Schema(implementation = WorkingSetResponse.class))))),
      @RouterOperation(path = "/api/dsl/objects/search", beanClass = DslIntrospectionHandler.class, beanMethod = "searchObjects", method = RequestMethod.GET, operation = @Operation(operationId = "searchObjects", summary = "Search DSL objects", tags = {
          "DSL Introspection"}, parameters = {@Parameter(name = "page", in = ParameterIn.QUERY),
              @Parameter(name = "size", in = ParameterIn.QUERY),
              @Parameter(name = "query", in = ParameterIn.QUERY),
              @Parameter(name = "mode", in = ParameterIn.QUERY),
              @Parameter(name = "type", in = ParameterIn.QUERY)}, responses = @ApiResponse(responseCode = "200", description = "Search results", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/dsl/{type}/{name}/construct", beanClass = DslIntrospectionHandler.class, beanMethod = "constructBody", method = RequestMethod.GET, operation = @Operation(operationId = "getConstructBody", summary = "Get DSL construct body", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "type", in = ParameterIn.PATH, description = "Construct type: processes, transactions, functions or helpers"),
              @Parameter(name = "name", in = ParameterIn.PATH)}, responses = @ApiResponse(responseCode = "200", description = "Construct body", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConstructBodyDto.class))))),
      @RouterOperation(path = "/api/dsl/{type}/{name}/schema", beanClass = DslIntrospectionHandler.class, beanMethod = "constructSchema", method = RequestMethod.GET, operation = @Operation(operationId = "getConstructSchema", summary = "Get DSL construct input/output schemas (preview) or explain report (mode=explain)", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "type", in = ParameterIn.PATH, description = "Construct type: processes, transactions, functions or helpers"),
              @Parameter(name = "name", in = ParameterIn.PATH),
              @Parameter(name = "mode", in = ParameterIn.QUERY, description = "Schema mode: preview (default) or explain")}, responses = @ApiResponse(responseCode = "200", description = "Construct schemas or explain report", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ConstructSchemaDto.class))))),
      @RouterOperation(path = "/api/dsl/{type}/{name}/structure", beanClass = DslIntrospectionHandler.class, beanMethod = "objectStructure", method = RequestMethod.GET, operation = @Operation(operationId = "getObjectStructure", summary = "Get DSL object structure", tags = {
          "DSL Introspection"}, parameters = {
              @Parameter(name = "type", in = ParameterIn.PATH, description = "Construct type: processes, transactions, functions or helpers"),
              @Parameter(name = "name", in = ParameterIn.PATH)}, responses = @ApiResponse(responseCode = "200", description = "Object structure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ObjectStructureDto.class)))))
  })
  public RouterFunction<ServerResponse> dslIntrospectionRouter(DslIntrospectionHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/working-set", handler::workingSet)
            .GET("/api/dsl/objects/search", handler::searchObjects)
            .GET("/api/dsl/{type}/{name}/construct", handler::constructBody)
            .GET("/api/dsl/{type}/{name}/schema", handler::constructSchema)
            .GET("/api/dsl/{type}/{name}/structure", handler::objectStructure)
            .build();
  }
}
