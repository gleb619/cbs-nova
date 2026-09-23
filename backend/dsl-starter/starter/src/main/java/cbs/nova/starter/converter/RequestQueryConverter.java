package cbs.nova.starter.converter;

import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode;
import cbs.nova.starter.model.RequestQueryModels.ExecutionListQuery;
import cbs.nova.starter.model.RequestQueryModels.ObjectSearchQuery;
import cbs.nova.starter.model.RequestQueryModels.WorkingSetQuery;
import cbs.nova.starter.controller.Pagination;
import cbs.nova.starter.core.StarterConstants;
import org.springframework.stereotype.Component;

/**
 * Translates a {@link org.springframework.web.servlet.function.ServerRequest} into typed query
 * records.
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
  public ExecutionListQuery toExecutionListQuery(
          org.springframework.web.servlet.function.ServerRequest request) {
    return new ExecutionListQuery(
            request.param("processName").filter(s -> !s.isBlank()).orElse(null),
            request.param("status").orElse(null),
            request.param("mode").orElse(null),
            request.param("correlationId").map(String::trim).filter(s -> !s.isBlank())
                    .orElse(null));
  }

  /**
   * Extracts the unified object-search pagination, query text, and search mode.
   */
  public ObjectSearchQuery toObjectSearchQuery(
          org.springframework.web.servlet.function.ServerRequest request) {
    int page = Pagination.intParam(request, "page", 0);
    int size = Pagination.intParam(request, "size", StarterConstants.DEFAULT_LIMIT);
    int clampedSize = Pagination.clampLimit(size);
    String query = request.param("query").map(String::trim).filter(s -> !s.isBlank()).orElse(null);
    ObjectSearchMode mode = ObjectSearchMode.from(request.param("mode").orElse(null));
    return new ObjectSearchQuery(Math.max(0, page), clampedSize, query, mode);
  }

  /**
   * Extracts the working-set pagination and filter parameters.
   */
  public WorkingSetQuery toWorkingSetQuery(
          org.springframework.web.servlet.function.ServerRequest request) {
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
