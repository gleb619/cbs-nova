package cbs.nova.starter.config;

import cbs.nova.starter.controllers.DslReloadHandler;
import cbs.nova.starter.models.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@AutoConfiguration
@ConditionalOnProperty(prefix = "dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslReloadRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/reload", beanClass = DslReloadHandler.class, beanMethod = "reload", method = RequestMethod.POST, operation = @Operation(operationId = "reload", summary = "Reload DSL definitions from configured source directory", tags = {
          "DSL Admin"}, responses = {
              @ApiResponse(responseCode = "204", description = "Reload successful"),
              @ApiResponse(responseCode = "409", description = "Source directory not configured or not found", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
              @ApiResponse(responseCode = "500", description = "Reload failed", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
          }))
  })
  RouterFunction<ServerResponse> dslReloadRouter(DslReloadHandler reloadHandler) {
    return RouterFunctions.route()
            .POST("/api/dsl/reload", reloadHandler::reload)
            .build();
  }
}
