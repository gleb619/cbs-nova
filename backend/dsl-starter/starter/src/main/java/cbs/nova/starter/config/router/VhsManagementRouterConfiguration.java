package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.VhsConfiguration;
import cbs.nova.starter.config.properties.CbsVhsReplayProperties;
import cbs.nova.starter.controller.VhsManagementHandler;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.vhs.loadtest.VhsLoadTest;
import cbs.nova.starter.vhs.management.ReplayRunResponse;
import cbs.nova.starter.vhs.management.TapeSummary;
import cbs.nova.starter.vhs.management.VhsManagementService;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import java.util.Map;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Registers the VHS tape management routes under {@code /api/v1/vhs/tapes}.
 *
 * <p>
 * The whole router is conditional on a {@link cbs.nova.starter.vhs.management.VhsTapeStore} bean,
 * so the routes vanish when VHS is not configured. Because the path sits under {@code /api/**} the
 * existing API-key and RBAC filters apply automatically.
 */
@AutoConfiguration
@AutoConfigureAfter(VhsConfiguration.class)
@ConditionalOnBean(cbs.nova.starter.vhs.management.VhsTapeStore.class)
public class VhsManagementRouterConfiguration {

  @Bean
  VhsManagementHandler vhsManagementHandler(VhsManagementService service,
          ObjectProvider<VhsLoadTest> loadTestProvider, ObjectMapper objectMapper) {
    return new VhsManagementHandler(service, loadTestProvider.getIfAvailable(), objectMapper);
  }

  @Bean
  @ConditionalOnBean({cbs.nova.starter.vhs.management.VhsTapeStore.class,
      cbs.nova.starter.vhs.replay.VhsReplayEngine.class})
  VhsLoadTest vhsLoadTest(io.micrometer.core.instrument.MeterRegistry meterRegistry,
          CbsVhsReplayProperties replayProperties) {
    return new VhsLoadTest(meterRegistry, replayProperties);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/v1/vhs/tapes", beanClass = VhsManagementHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listVhsTapes", summary = "List recorded VHS tapes", tags = {
          "VHS Tapes"}, parameters = {
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of tapes to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of tapes to skip")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/v1/vhs/tapes/{runId}", beanClass = VhsManagementHandler.class, beanMethod = "download", method = RequestMethod.GET, operation = @Operation(operationId = "downloadVhsTape", summary = "Download a VHS tape", tags = {
          "VHS Tapes"}, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/x-jsonl", schema = @Schema(implementation = String.class))))),
      @RouterOperation(path = "/api/v1/vhs/tapes/{runId}", beanClass = VhsManagementHandler.class, beanMethod = "delete", method = RequestMethod.DELETE, operation = @Operation(operationId = "deleteVhsTape", summary = "Delete a VHS tape", tags = {
          "VHS Tapes"}, responses = {
              @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))),
              @ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/v1/vhs/tapes/{runId}/replay", beanClass = VhsManagementHandler.class, beanMethod = "replay", method = RequestMethod.POST, operation = @Operation(operationId = "replayVhsTape", summary = "Replay a VHS tape", tags = {
          "VHS Tapes"}, responses = {
              @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReplayRunResponse.class))),
              @ApiResponse(responseCode = "202", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReplayRunResponse.class))),
              @ApiResponse(responseCode = "404", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/v1/vhs/loadtest", beanClass = VhsManagementHandler.class, beanMethod = "loadtest", method = RequestMethod.POST, operation = @Operation(operationId = "vhsLoadTest", summary = "Run a VHS load test against multiple tapes", tags = {
          "VHS Tapes"}, responses = {
              @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json")),
              @ApiResponse(responseCode = "400", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "503", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> vhsManagementRouter(VhsManagementHandler handler) {
    return RouterFunctions.route()
            .GET("/api/v1/vhs/tapes", handler::list)
            .GET("/api/v1/vhs/tapes/{runId}", handler::download)
            .DELETE("/api/v1/vhs/tapes/{runId}", handler::delete)
            .POST("/api/v1/vhs/tapes/{runId}/replay", handler::replay)
            .POST("/api/v1/vhs/loadtest", handler::loadtest)
            .build();
  }
}
