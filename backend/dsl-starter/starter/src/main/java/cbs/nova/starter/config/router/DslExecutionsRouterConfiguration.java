package cbs.nova.starter.config.router;

import cbs.nova.dsl.history.DslRunRepository;
import cbs.nova.dsl.history.TransactionExecutionRepository;
import cbs.nova.starter.controller.DslExecutionsHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ExecutionDto;
import cbs.nova.starter.model.ExecutionListResponse;
import cbs.nova.starter.model.ExecutionStatsResponse;
import cbs.nova.starter.model.ExecutionTimeseriesResponse;
import cbs.nova.starter.model.TransactionExecutionDto;
import cbs.nova.starter.persistence.DslRunStatsRepository;
import cbs.nova.starter.service.DslRunCancellationService;
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
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DslExecutionsRouterConfiguration {

  @Bean
  DslExecutionsHandler dslExecutionsHandler(DslRunRepository runRepository,
          ObjectMapper objectMapper,
          DslRunCancellationService dslRunCancellationService,
          TransactionExecutionRepository transactionExecutionRepository) {
    DslRunStatsRepository statsRepository = runRepository instanceof DslRunStatsRepository stats
            ? stats
            : null;
    return new DslExecutionsHandler(runRepository, objectMapper, dslRunCancellationService,
            statsRepository, transactionExecutionRepository);
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
      // Routed before "/api/executions/{id}" below so the literal "stats" segment wins over {id}.
      @RouterOperation(path = "/api/executions/stats", beanClass = DslExecutionsHandler.class, beanMethod = "stats", method = RequestMethod.GET, operation = @Operation(operationId = "getExecutionStats", summary = "Aggregate DSL execution run statistics", tags = {
          "DSL Executions"}, parameters = {
              @Parameter(name = "topProcesses", in = ParameterIn.QUERY, description = "Maximum number of processes in the top-by-run-count list")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionStatsResponse.class))))),
      // Same literal-before-{id} rule as /stats: "timeseries" must precede "/api/executions/{id}"
      // or the segment would be captured as a run id.
      @RouterOperation(path = "/api/executions/stats/timeseries", beanClass = DslExecutionsHandler.class, beanMethod = "timeseries", method = RequestMethod.GET, operation = @Operation(operationId = "getExecutionTimeseries", summary = "Per-bucket run counts grouped by status", tags = {
          "DSL Executions"}, parameters = {
              @Parameter(name = "windowHours", in = ParameterIn.QUERY, description = "Trailing window in hours; clamped to [1, 720]"),
              @Parameter(name = "bucketMinutes", in = ParameterIn.QUERY, description = "Bucket width in minutes; clamped to [1, 1440]")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionTimeseriesResponse.class))))),
      // Same literal-before-{id} rule: "export.csv" must precede "/api/executions/{id}" or the
      // segment would be captured as a run id.
      @RouterOperation(path = "/api/executions/export.csv", beanClass = DslExecutionsHandler.class, beanMethod = "exportCsv", method = RequestMethod.GET, operation = @Operation(operationId = "exportExecutionsCsv", summary = "Export DSL execution runs as CSV", tags = {
          "DSL Executions"}, parameters = {
              @Parameter(name = "processName", in = ParameterIn.QUERY),
              @Parameter(name = "status", in = ParameterIn.QUERY),
              @Parameter(name = "mode", in = ParameterIn.QUERY),
              @Parameter(name = "correlationId", in = ParameterIn.QUERY)
          }, responses = @ApiResponse(responseCode = "200", description = "CSV export of matching execution runs", content = @Content(mediaType = "text/csv")))),
      @RouterOperation(path = "/api/executions/{id}", beanClass = DslExecutionsHandler.class, beanMethod = "detail", method = RequestMethod.GET, operation = @Operation(operationId = "getExecution", summary = "Get a single DSL execution run by id", tags = {
          "DSL Executions"}, parameters = @Parameter(name = "id", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "The execution run", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ExecutionDto.class))),
              @ApiResponse(responseCode = "404", description = "No run with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/executions/{id}/transactions", beanClass = DslExecutionsHandler.class, beanMethod = "transactions", method = RequestMethod.GET, operation = @Operation(operationId = "getExecutionTransactions", summary = "List transaction executions for a run", tags = {
          "DSL Executions"}, parameters = @Parameter(name = "id", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "200", description = "Transaction executions for the run, most recent first", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = TransactionExecutionDto.class)))),
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
            // Literal routes must precede "/api/executions/{id}" or "stats"/"timeseries" would be
            // captured as an id.
            .GET("/api/executions/stats", handler::stats)
            .GET("/api/executions/stats/timeseries", handler::timeseries)
            .GET("/api/executions/export.csv", handler::exportCsv)
            .GET("/api/executions/{id}", handler::detail)
            .GET("/api/executions/{id}/transactions", handler::transactions)
            .POST("/api/executions/{id}/cancel", handler::cancel)
            .build();
  }
}
