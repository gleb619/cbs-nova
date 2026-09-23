package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.model.RequestQueryModels.ExecutionListQuery;
import cbs.nova.starter.model.RequestQueryModels.HelperCatalogQuery;
import cbs.nova.starter.model.RequestQueryModels.IntrospectionSearchQuery;
import cbs.nova.starter.model.RequestQueryModels.WorkingSetQuery;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;

/**
 * Pins the parsing rules extracted from {@link cbs.nova.starter.controller.DslExecutionsHandler}
 * and {@link cbs.nova.starter.controller.DslIntrospectionHandler}.
 *
 * <p>
 * {@link ServerRequest#param} returns {@code Optional<String>}; per-field rules differ — see the
 * converter Javadoc. These tests fail loudly if any rule regresses, which would otherwise change
 * the wire contract of {@code /api/executions} and {@code /api/dsl/objects/search}.
 */
class RequestQueryConverterTest {

  /**
   * Mirrors the helper used by {@code DslDraftResourceTest} so we exercise the same
   * {@link ServerRequest#create} plumbing the handlers see in production tests.
   */
  private static final List<HttpMessageConverter<?>> CONVERTERS = List.of();

  private final RequestQueryConverter converter = new RequestQueryConverter();

  private static ServerRequest request(Map<String, String> params) {
    var req = new MockHttpServletRequest("GET", "/");
    params.forEach(req::addParameter);
    req.setAttribute(RouterFunctions.URI_TEMPLATE_VARIABLES_ATTRIBUTE, Map.of());
    return ServerRequest.create(req, CONVERTERS);
  }

  // --- toExecutionListQuery ---------------------------------------------------

  @Test
  void executionListQueryBlankProcessNameBecomesNull() {
    ExecutionListQuery q = converter.toExecutionListQuery(request(Map.of("processName", "  ")));
    assertThat(q.processName()).isNull();
  }

  @Test
  void executionListQueryNonBlankProcessNamePassesThrough() {
    ExecutionListQuery q = converter.toExecutionListQuery(request(Map.of("processName", "proc")));
    assertThat(q.processName()).isEqualTo("proc");
  }

  @Test
  void executionListQueryBlankStatusPassesThrough() {
    // status intentionally does NOT blank-filter — empty string is a meaningful filter.
    ExecutionListQuery q = converter.toExecutionListQuery(request(Map.of("status", "")));
    assertThat(q.status()).isEqualTo("");
  }

  @Test
  void executionListQueryBlankModePassesThrough() {
    ExecutionListQuery q = converter.toExecutionListQuery(request(Map.of("mode", "")));
    assertThat(q.mode()).isEqualTo("");
  }

  @Test
  void executionListQueryCorrelationIdIsTrimmed() {
    ExecutionListQuery q = converter.toExecutionListQuery(
            request(Map.of("correlationId", "  abc  ")));
    assertThat(q.correlationId()).isEqualTo("abc");
  }

  @Test
  void executionListQueryBlankCorrelationIdBecomesNull() {
    ExecutionListQuery q = converter.toExecutionListQuery(
            request(Map.of("correlationId", "   ")));
    assertThat(q.correlationId()).isNull();
  }

  @Test
  void executionListQueryAllAbsentAreNull() {
    ExecutionListQuery q = converter.toExecutionListQuery(request(Map.of()));
    assertThat(q.processName()).isNull();
    assertThat(q.status()).isNull();
    assertThat(q.mode()).isNull();
    assertThat(q.correlationId()).isNull();
  }

  // --- toIntrospectionSearchQuery --------------------------------------------

  @Test
  void introspectionSearchQueryAbsentAreNull() {
    IntrospectionSearchQuery q = converter.toIntrospectionSearchQuery(request(Map.of()));
    assertThat(q.name()).isNull();
    assertThat(q.type()).isNull();
    assertThat(q.description()).isNull();
  }

  @Test
  void introspectionSearchQueryValuesPassThrough() {
    IntrospectionSearchQuery q = converter.toIntrospectionSearchQuery(
            request(Map.of("name", "x", "type", "y", "description", "z")));
    assertThat(q.name()).isEqualTo("x");
    assertThat(q.type()).isEqualTo("y");
    assertThat(q.description()).isEqualTo("z");
  }

  @Test
  void introspectionSearchQueryBlankValuesPassThrough() {
    // No blank filter on the search endpoint — empty strings stay empty.
    IntrospectionSearchQuery q = converter.toIntrospectionSearchQuery(
            request(Map.of("name", "", "type", "", "description", "")));
    assertThat(q.name()).isEqualTo("");
    assertThat(q.type()).isEqualTo("");
    assertThat(q.description()).isEqualTo("");
  }

  // --- toHelperCatalogQuery -------------------------------------------------

  @Test
  void helperCatalogQueryAbsentAreNull() {
    HelperCatalogQuery q = converter.toHelperCatalogQuery(request(Map.of()));
    assertThat(q.text()).isNull();
    assertThat(q.mode()).isNull();
  }

  @Test
  void helperCatalogQueryValuesPassThrough() {
    HelperCatalogQuery q = converter.toHelperCatalogQuery(
            request(Map.of("search", "foo", "searchMode", "fuzzy")));
    assertThat(q.text()).isEqualTo("foo");
    assertThat(q.mode()).isEqualTo("fuzzy");
  }

  @Test
  void helperCatalogQueryBlankValuesBecomeNull() {
    HelperCatalogQuery q = converter.toHelperCatalogQuery(
            request(Map.of("search", "   ", "searchMode", "  ")));
    assertThat(q.text()).isNull();
    assertThat(q.mode()).isNull();
  }

  @Test
  void helperCatalogQueryTextIsTrimmed() {
    HelperCatalogQuery q = converter.toHelperCatalogQuery(
            request(Map.of("search", "  foo  ")));
    assertThat(q.text()).isEqualTo("foo");
  }

  // --- toWorkingSetQuery ----------------------------------------------------

  @Test
  void workingSetQueryDefaultsLimitAndOffset() {
    WorkingSetQuery q = converter.toWorkingSetQuery(request(Map.of()));
    assertThat(q.limit()).isEqualTo(50);
    assertThat(q.offset()).isEqualTo(0);
    assertThat(q.name()).isNull();
    assertThat(q.type()).isNull();
    assertThat(q.description()).isNull();
  }

  @Test
  void workingSetQueryParsesLimitOffsetAndFilters() {
    WorkingSetQuery q = converter.toWorkingSetQuery(
            request(Map.of("limit", "10", "offset", "5", "name", "x", "type", "y",
                    "description", "z")));
    assertThat(q.limit()).isEqualTo(10);
    assertThat(q.offset()).isEqualTo(5);
    assertThat(q.name()).isEqualTo("x");
    assertThat(q.type()).isEqualTo("y");
    assertThat(q.description()).isEqualTo("z");
  }
}
