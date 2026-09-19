package cbs.nova.starter.config.router;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.controller.DslPromoteHandler;
import cbs.nova.starter.model.PromotionModels.PromotionDefinition;
import cbs.nova.starter.model.PromotionModels.PromotionEnvironment;
import cbs.nova.starter.model.PromotionModels.PromotionRequest;
import cbs.nova.starter.model.VcsModels.ImportBundleResult;
import io.swagger.v3.oas.annotations.Operation;
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

/**
 * Routes for the T569 environment-promotion workflow. Mutating promotion requires
 * {@code Role.OPERATOR} (see the RBAC filter); the read routes default to VIEWER.
 */
@Configuration
public class DslPromoteRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/promote/environments", beanClass = DslPromoteHandler.class, beanMethod = "environments", method = RequestMethod.GET, operation = @Operation(operationId = "promotionEnvironments", summary = "List configured promotion environments", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Environment names", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PromotionEnvironment.class)))
          })),
      @RouterOperation(path = "/api/dsl/promote/definitions", beanClass = DslPromoteHandler.class, beanMethod = "definitions", method = RequestMethod.GET, operation = @Operation(operationId = "promotionDefinitions", summary = "List definitions selectable in a source environment", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Definitions in the environment", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PromotionDefinition.class))),
              @ApiResponse(responseCode = "404", description = "Environment not configured or directory missing", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          })),
      @RouterOperation(path = "/api/dsl/promote", beanClass = DslPromoteHandler.class, beanMethod = "promote", method = RequestMethod.POST, operation = @Operation(operationId = "promote", summary = "Promote a definition bundle from one environment to another (dry-run or apply)", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "200", description = "Dry-run diff or apply result", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ImportBundleResult.class))),
              @ApiResponse(responseCode = "400", description = "Invalid request or bundle validation failure", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "404", description = "Source or target environment not configured", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  public RouterFunction<ServerResponse> dslPromoteRouter(DslPromoteHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/promote/environments", handler::environments)
            .GET("/api/dsl/promote/definitions", handler::definitions)
            .POST("/api/dsl/promote", handler::promote)
            .build();
  }

}
