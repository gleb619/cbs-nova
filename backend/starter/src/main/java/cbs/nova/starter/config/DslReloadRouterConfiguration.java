package cbs.nova.starter.config;

import cbs.nova.starter.controllers.DslReloadResource;
import io.swagger.v3.oas.annotations.Operation;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Registers the DSL reload endpoint as a functional {@link RouterFunction} bean. The route is only
 * wired when {@code dsl.reload.enabled} is true (default), delegating to {@link DslReloadResource}.
 * The {@link RouterOperations} metadata keeps the route visible in the springdoc-generated OpenAPI
 * spec, which does not introspect functional endpoints by default.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslReloadRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/reload", method = RequestMethod.POST, operation = @Operation(method = "POST", summary = "Reload DSL definitions from configured source directory", operationId = "reload", tags = {
          "DSL Admin"}, parameters = {}, responses = {}))
  })
  RouterFunction<ServerResponse> dslReloadRouter(DslReloadResource reloadResource) {
    return RouterFunctions.route()
            .POST("/api/dsl/reload", reloadResource::reload)
            .build();
  }
}
