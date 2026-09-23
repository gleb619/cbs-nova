package cbs.nova.starter.converter;

import cbs.nova.starter.model.RequestQueryModels.ExecutionListQuery;
import cbs.nova.starter.model.RequestQueryModels.HelperCatalogQuery;
import cbs.nova.starter.model.RequestQueryModels.IntrospectionSearchQuery;
import cbs.nova.starter.model.RequestQueryModels.WorkingSetQuery;
import cbs.nova.starter.controller.Pagination;
import cbs.nova.starter.core.StarterConstants;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Translates a {@link ServerRequest} into typed query records.
 *
 * <p>
 * Hand-written on purpose: the mapping is not a bean property copy, so neither MapStruct nor
 * Spring's {@code -parameters} reflection binding help here. {@code ServerRequest.param} returns
 * {@code Optional<String>} values that need per-field handling (blank filters, trims) which
 * MapStruct cannot express; an auto-generated {@code @Mapper} interface with only default methods
 * would also produce no Spring bean to inject. A plain {@code @Component} keeps the extraction
 * visible, testable, and dependency-free.
 *
 * <p>
 * The parsing rules here are copied verbatim from the previous handler implementations and are
 * load-bearing for the existing wire contract — do not "simplify" them without bumping the affected
 * handler tests.
 */
@Component
public class RequestQueryConverter {

  /**
   * Extracts the four execution-list filters. {@code processName} blanks out (without trimming),
   * {@code status} and {@code mode} pass through verbatim, and {@code correlationId} is trimmed
   * then blank-filtered.
   */
  public ExecutionListQuery toExecutionListQuery(ServerRequest request) {
    return new ExecutionListQuery(
            request.param("processName").filter(s -> !s.isBlank()).orElse(null),
            request.param("status").orElse(null),
            request.param("mode").orElse(null),
            request.param("correlationId").map(String::trim).filter(s -> !s.isBlank())
                    .orElse(null));
  }

  /**
   * Extracts the three introspection search params as plain passthroughs.
   */
  public IntrospectionSearchQuery toIntrospectionSearchQuery(ServerRequest request) {
    return new IntrospectionSearchQuery(
            request.param("name").orElse(null),
            request.param("type").orElse(null),
            request.param("description").orElse(null));
  }

  /**
   * Extracts the helper-catalog filters. {@code text} is blank-filtered; {@code mode} is
   * blank-filtered and defaults to {@code null} so the service can pick its own default.
   */
  public HelperCatalogQuery toHelperCatalogQuery(ServerRequest request) {
    return new HelperCatalogQuery(
            request.param("search").map(String::trim).filter(s -> !s.isBlank()).orElse(null),
            request.param("searchMode").map(String::trim).filter(s -> !s.isBlank())
                    .orElse(null));
  }

  /**
   * Extracts the working-set pagination and filter parameters.
   */
  public WorkingSetQuery toWorkingSetQuery(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    return new WorkingSetQuery(
            limit,
            offset,
            request.param("name").orElse(null),
            request.param("type").orElse(null),
            request.param("description").orElse(null));
  }
}
