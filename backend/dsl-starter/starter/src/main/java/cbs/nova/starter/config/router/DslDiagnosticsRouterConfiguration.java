package cbs.nova.starter.config.router;

import cbs.nova.starter.config.DslRunRepositoryConfiguration;
import cbs.nova.starter.controller.DslDiagnosticsHandler;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

import javax.sql.DataSource;

/**
 * Registers the read side of the append-only compile diagnostic log under
 * {@code GET /api/dsl/diagnostics}.
 *
 * <p>
 * Only registered when a {@link DataSource} is present, because the underlying
 * {@link CompileDiagnosticRecordRepository} is conditional on the same bean.
 */
@AutoConfiguration
@AutoConfigureAfter({DataSourceAutoConfiguration.class, DslRunRepositoryConfiguration.class})
@ConditionalOnBean(DataSource.class)
public class DslDiagnosticsRouterConfiguration {

  @Bean
  DslDiagnosticsHandler dslDiagnosticsHandler(CompileDiagnosticRecordRepository repository) {
    return new DslDiagnosticsHandler(repository);
  }

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/diagnostics", beanClass = DslDiagnosticsHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listCompileDiagnostics", summary = "List persisted compile diagnostics, newest first", tags = {
          "DSL Admin"}, parameters = {
              @Parameter(name = "definition", in = ParameterIn.QUERY, description = "Optional exact definition context filter"),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of diagnostics to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching diagnostics to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))))
  })
  public RouterFunction<ServerResponse> dslDiagnosticsRouter(DslDiagnosticsHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/diagnostics", handler::list)
            .build();
  }
}
