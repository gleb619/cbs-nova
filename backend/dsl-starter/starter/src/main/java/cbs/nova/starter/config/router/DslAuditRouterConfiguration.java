package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.DslAuditHandler;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.DslAuditRepository;
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
 * Registers the read side of the append-only audit log under {@code GET /api/dsl/audit}.
 *
 * <p>
 * Requires a {@link DslAuditRepository} bean (created when a {@code DataSource} is present), the
 * same implicit requirement as the executions endpoints' handlers. Note: {@code @ConditionalOnBean}
 * does not work here — this class is component-scanned from {@code cbs.nova.starter}, so its
 * conditions evaluate before auto-configuration bean definitions (like {@code DslAuditRepository})
 * are registered.
 */
@Configuration
public class DslAuditRouterConfiguration {

  @Bean
  DslAuditHandler dslAuditHandler(DslAuditRepository auditRepository) {
    return new DslAuditHandler(auditRepository);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/audit", beanClass = DslAuditHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listAuditEntries", summary = "List append-only control-plane audit entries, newest first", tags = {
          "DSL Audit"}, parameters = {
              @Parameter(name = "action", in = ParameterIn.QUERY, description = "Optional exact action filter (e.g. DEFINITION_PUBLISH)"),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of entries to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching entries to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))))
  })
  public RouterFunction<ServerResponse> dslAuditRouter(DslAuditHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/audit", handler::list)
            .build();
  }
}
