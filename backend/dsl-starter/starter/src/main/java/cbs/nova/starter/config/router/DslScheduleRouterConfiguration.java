package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslScheduleHandler;
import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.model.PageResponse;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import io.temporal.client.schedules.ScheduleClient;

/**
 * Registers the DSL schedule CRUD routes under {@code /api/dsl/schedules}. The whole router is
 * conditional on a {@link io.temporal.client.schedules.ScheduleClient} bean, so the routes vanish
 * when Temporal is not configured.
 */
@Configuration
@ConditionalOnBean(ScheduleClient.class)
public class DslScheduleRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/schedules", beanClass = DslScheduleHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listSchedules", summary = "List Temporal schedules for published DSL definitions", tags = {
          "DSL Schedules"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/dsl/schedules", beanClass = DslScheduleHandler.class, beanMethod = "create", method = RequestMethod.POST, operation = @Operation(operationId = "createSchedule", summary = "Create a Temporal schedule that starts a DSL definition workflow", tags = {
          "DSL Schedules"}, responses = {
              @ApiResponse(responseCode = "201", description = "Schedule created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = cbs.nova.starter.model.ScheduleModels.CreateScheduleResponse.class))),
              @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Definition not published", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Schedule already exists", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/schedules/{definition}/pause", beanClass = DslScheduleHandler.class, beanMethod = "pause", method = RequestMethod.POST, operation = @Operation(operationId = "pauseSchedule", summary = "Pause the Temporal schedule for a DSL definition", tags = {
          "DSL Schedules"}, responses = {
              @ApiResponse(responseCode = "200", description = "Schedule paused", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
              @ApiResponse(responseCode = "400", description = "Invalid definition name", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Schedule not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/schedules/{definition}/resume", beanClass = DslScheduleHandler.class, beanMethod = "resume", method = RequestMethod.POST, operation = @Operation(operationId = "resumeSchedule", summary = "Resume the Temporal schedule for a DSL definition", tags = {
          "DSL Schedules"}, responses = {
              @ApiResponse(responseCode = "200", description = "Schedule resumed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
              @ApiResponse(responseCode = "400", description = "Invalid definition name", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Schedule not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/schedules/{definition}", beanClass = DslScheduleHandler.class, beanMethod = "delete", method = RequestMethod.DELETE, operation = @Operation(operationId = "deleteSchedule", summary = "Delete the Temporal schedule for a DSL definition", tags = {
          "DSL Schedules"}, responses = {
              @ApiResponse(responseCode = "200", description = "Schedule deleted", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
              @ApiResponse(responseCode = "400", description = "Invalid definition name", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslScheduleRouter(DslScheduleHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/schedules", handler::list)
            .POST("/api/dsl/schedules", handler::create)
            .POST("/api/dsl/schedules/{definition}/pause", handler::pause)
            .POST("/api/dsl/schedules/{definition}/resume", handler::resume)
            .DELETE("/api/dsl/schedules/{definition}", handler::delete)
            .build();
  }
}
