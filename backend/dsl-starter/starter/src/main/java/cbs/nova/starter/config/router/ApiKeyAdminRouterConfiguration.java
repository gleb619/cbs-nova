package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.ApiKeyAdminHandler;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.ApiKeyStore;
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

/**
 * Router for the admin API-key surface (T410). Wires {@link ApiKeyAdminHandler} under
 * {@code /api/dsl/auth/keys}. Auth is performed upstream by
 * {@link cbs.nova.starter.web.ApiKeyAuthFilter}; RBAC (when enabled) additionally requires
 * {@link cbs.nova.starter.security.Role#OPERATOR} via the rules table in
 * {@link cbs.nova.starter.security.RbacAuthorizationFilter}.
 */
@Configuration
public class ApiKeyAdminRouterConfiguration {

  @Bean
  ApiKeyAdminHandler apiKeyAdminHandler(ApiKeyStore store) {
    return new ApiKeyAdminHandler(store);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/auth/keys", beanClass = ApiKeyAdminHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listApiKeys", summary = "List stored API keys (label, prefix, timestamps). NEVER the hash or plaintext.", tags = {
          "DSL Auth"})),
      @RouterOperation(path = "/api/dsl/auth/keys", beanClass = ApiKeyAdminHandler.class, beanMethod = "create", method = RequestMethod.POST, operation = @Operation(operationId = "createApiKey", summary = "Create a new API key. Returns the plaintext exactly once.", tags = {
          "DSL Auth"}, responses = {
              @ApiResponse(responseCode = "200", description = "Key created; plaintext returned in the response body"),
              @ApiResponse(responseCode = "400", description = "Missing or invalid 'label'", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))})),
      @RouterOperation(path = "/api/dsl/auth/keys/{id}", beanClass = ApiKeyAdminHandler.class, beanMethod = "revoke", method = RequestMethod.DELETE, operation = @Operation(operationId = "revokeApiKey", summary = "Revoke a stored API key by id. Idempotent for already-revoked ids (404 when unknown).", tags = {
          "DSL Auth"}, parameters = @Parameter(name = "id", in = ParameterIn.PATH), responses = {
              @ApiResponse(responseCode = "204", description = "Key revoked"),
              @ApiResponse(responseCode = "404", description = "Unknown id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))}))
  })
  public RouterFunction<ServerResponse> apiKeyAdminRouter(ApiKeyAdminHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/auth/keys", handler::list)
            .POST("/api/dsl/auth/keys", handler::create)
            .DELETE("/api/dsl/auth/keys/{id}", handler::revoke)
            .build();
  }
}
