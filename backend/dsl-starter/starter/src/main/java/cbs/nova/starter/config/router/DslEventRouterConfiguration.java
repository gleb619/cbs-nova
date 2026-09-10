package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslEventHandler;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.DslEventRepository;
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
public class DslEventRouterConfiguration {

  @Bean
  DslEventHandler dslEventHandler(DslEventRepository repository, ObjectMapper objectMapper) {
    return new DslEventHandler(repository, objectMapper);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/events", beanClass = DslEventHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listDomainEvents", summary = "List append-only domain events, newest first", tags = {
          "DSL Events"}, parameters = {
              @Parameter(name = "type", in = ParameterIn.QUERY, description = "Optional exact event_type filter (e.g. RunCompleted)"),
              @Parameter(name = "aggregateType", in = ParameterIn.QUERY, description = "Optional exact aggregate_type filter (run|draft|definition)"),
              @Parameter(name = "aggregateId", in = ParameterIn.QUERY, description = "Optional exact aggregate_id filter (runId or definition name)"),
              @Parameter(name = "correlationId", in = ParameterIn.QUERY, description = "Optional exact correlation_id filter"),
              @Parameter(name = "since", in = ParameterIn.QUERY, description = "Optional ISO-8601 instant lower bound on created_at (inclusive)"),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of events to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching events to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))))
  })
  public RouterFunction<ServerResponse> dslEventRouter(DslEventHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/events", handler::list)
            .build();
  }
}
