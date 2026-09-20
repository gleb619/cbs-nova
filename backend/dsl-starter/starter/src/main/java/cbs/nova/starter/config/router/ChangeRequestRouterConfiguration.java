package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.service.ChangeRequestService;
import cbs.nova.starter.controller.ChangeRequestHandler;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.security.RoleResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * T568 change-request approval gate routes. Conditional on {@link ChangeRequestService} (which
 * itself is conditional on a DataSource via {@code ChangeRequestConfiguration}): no service bean,
 * no routes — the same infra-gating idiom as {@code NotificationRouterConfiguration}.
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnBean(ChangeRequestService.class)
public class ChangeRequestRouterConfiguration {

  @Bean
  ChangeRequestHandler changeRequestHandler(ChangeRequestService changeRequestService,
          ObjectProvider<RoleResolver> roleResolverProvider, ObjectMapper objectMapper) {
    // Same shared RoleResolver PieceGuardFilter/RbacAuthorizationFilter use (resolved lazily so
    // minimal test contexts without a RoleResolver bean still boot). In practice the
    // RbacFilterConfiguration bean always wins; the fallback only mirrors its default-claim
    // behaviour for bare contexts.
    RoleResolver roleResolver = roleResolverProvider.getIfAvailable(
            () -> new RoleResolver(StarterConstants.DEFAULT_CLAIM_NAME));
    return new ChangeRequestHandler(changeRequestService, roleResolver, objectMapper);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/drafts/{name}/change-request", beanClass = ChangeRequestHandler.class, beanMethod = "submit", method = RequestMethod.POST, operation = @Operation(operationId = "submitChangeRequest", summary = "Submit a change request snapshotting the current draft for approval", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "201", description = "Change request created", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChangeRequestEntity.class))),
              @ApiResponse(responseCode = "404", description = "No draft exists for the definition", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/change-requests", beanClass = ChangeRequestHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listChangeRequests", summary = "List change requests, optionally filtered by definitionName and status", tags = {
          "DSL Admin"}, parameters = {
              @Parameter(name = "definitionName", in = ParameterIn.QUERY, description = "Optional exact definition name filter"),
              @Parameter(name = "status", in = ParameterIn.QUERY, description = "Optional status filter (PENDING, APPROVED, REJECTED, SUPERSEDED)")
          }, responses = @ApiResponse(responseCode = "200", description = "Matching change requests, newest first", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChangeRequestEntity.class))))),
      @RouterOperation(path = "/api/dsl/change-requests/{id}/approve", beanClass = ChangeRequestHandler.class, beanMethod = "approve", method = RequestMethod.POST, operation = @Operation(operationId = "approveChangeRequest", summary = "Approve a pending change request and publish its snapshot", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Publish result of the approved snapshot"),
              @ApiResponse(responseCode = "403", description = "Caller rank too low or self-approval", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "No change request with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Change request is not pending", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/change-requests/{id}/reject", beanClass = ChangeRequestHandler.class, beanMethod = "reject", method = RequestMethod.POST, operation = @Operation(operationId = "rejectChangeRequest", summary = "Reject a pending change request", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "The rejected change request", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ChangeRequestEntity.class))),
              @ApiResponse(responseCode = "403", description = "Caller rank too low or self-rejection", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "No change request with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "409", description = "Change request is not pending", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> changeRequestRouter(ChangeRequestHandler handler) {
    return RouterFunctions.route()
            .POST("/api/dsl/drafts/{name}/change-request", handler::submit)
            .GET("/api/dsl/change-requests", handler::list)
            .POST("/api/dsl/change-requests/{id}/approve", handler::approve)
            .POST("/api/dsl/change-requests/{id}/reject", handler::reject)
            .build();
  }
}
