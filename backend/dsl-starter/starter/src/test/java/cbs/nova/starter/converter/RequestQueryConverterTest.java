package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchMode;
import cbs.nova.starter.model.RequestQueryModels.ExecutionListQuery;
import cbs.nova.starter.model.RequestQueryModels.ObjectSearchQuery;
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

  // --- toObjectSearchQuery ----------------------------------------------------

  @Test
  void objectSearchQueryDefaultsPageSizeModeAndType() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(request(Map.of()));
    assertThat(q.page()).isEqualTo(0);
    assertThat(q.size()).isEqualTo(50);
    assertThat(q.query()).isNull();
    assertThat(q.mode()).isEqualTo(ObjectSearchMode.EXACT);
    assertThat(q.type()).isNull();
  }

  @Test
  void objectSearchQueryParsesPageSizeQueryAndMode() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(
            request(Map.of("page", "1", "size", "10", "query", "foo", "mode", "fuzzy")));
    assertThat(q.page()).isEqualTo(1);
    assertThat(q.size()).isEqualTo(10);
    assertThat(q.query()).isEqualTo("foo");
    assertThat(q.mode()).isEqualTo(ObjectSearchMode.FUZZY);
  }

  @Test
  void objectSearchQueryParsesDslType() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(request(Map.of("type", "process")));
    assertThat(q.type()).isEqualTo(DslType.PROCESS);
  }

  @Test
  void objectSearchQueryParsesDslTypeCaseInsensitively() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(request(Map.of("type", "Process")));
    assertThat(q.type()).isEqualTo(DslType.PROCESS);
  }

  @Test
  void objectSearchQueryBlankDslTypeBecomesNull() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(request(Map.of("type", "   ")));
    assertThat(q.type()).isNull();
  }

  @Test
  void objectSearchQueryUnknownDslTypeThrows() {
    assertThatThrownBy(() -> converter.toObjectSearchQuery(request(Map.of("type", "banana"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unknown DSL type: banana");
  }

  @Test
  void objectSearchQueryTrimsQueryAndBlankBecomesNull() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(
            request(Map.of("query", "  foo  ")));
    assertThat(q.query()).isEqualTo("foo");

    ObjectSearchQuery blank = converter.toObjectSearchQuery(
            request(Map.of("query", "   ")));
    assertThat(blank.query()).isNull();
  }

  @Test
  void objectSearchQueryClampsNegativePage() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(
            request(Map.of("page", "-3", "size", "10")));
    assertThat(q.page()).isEqualTo(0);
    assertThat(q.size()).isEqualTo(10);
  }

  @Test
  void objectSearchQueryDefaultsUnknownModeToExact() {
    ObjectSearchQuery q = converter.toObjectSearchQuery(
            request(Map.of("mode", "unknown")));
    assertThat(q.mode()).isEqualTo(ObjectSearchMode.EXACT);
  }
}
