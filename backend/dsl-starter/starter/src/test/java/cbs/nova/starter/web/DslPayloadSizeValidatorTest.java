package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.model.DslRequest;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.ObjectMapper;

class DslPayloadSizeValidatorTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsRequestAtExactSizeBoundary() throws Exception {
    long limit = 100L;
    DslRunsProperties properties = new DslRunsProperties();
    properties.setMaxInputBytes(limit);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(objectMapper, properties);

    int overhead = objectMapper.writeValueAsBytes(new DslRequest("", Map.of())).length;
    DslRequest dslRequest = new DslRequest("a".repeat((int) (limit - overhead)), Map.of());
    assertThat(objectMapper.writeValueAsBytes(dslRequest).length).isEqualTo(limit);

    validator.validateInput(mockRequestWithContentLength(-1), dslRequest, "P");
  }

  @Test
  void rejectsRequestOneByteOverBoundary() throws Exception {
    long limit = 100L;
    DslRunsProperties properties = new DslRunsProperties();
    properties.setMaxInputBytes(limit);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(objectMapper, properties);

    int overhead = objectMapper.writeValueAsBytes(new DslRequest("", Map.of())).length;
    DslRequest dslRequest = new DslRequest("a".repeat((int) (limit - overhead + 1)), Map.of());
    assertThat(objectMapper.writeValueAsBytes(dslRequest).length).isEqualTo(limit + 1);

    assertThatThrownBy(() -> validator.validateInput(mockRequestWithContentLength(-1), dslRequest,
            "P"))
            .isInstanceOf(DslPayloadTooLargeException.class)
            .satisfies(ex -> {
              DslPayloadTooLargeException e = (DslPayloadTooLargeException) ex;
              assertThat(e.getLimit()).isEqualTo(limit);
              assertThat(e.getActualBytes()).isEqualTo(limit + 1);
              assertThat(e.getEntityName()).isEqualTo("P");
            });
  }

  @Test
  void disablesInputCapWhenMaxIsZero() throws Exception {
    DslRunsProperties properties = new DslRunsProperties();
    properties.setMaxInputBytes(0L);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(objectMapper, properties);

    DslRequest dslRequest = new DslRequest("a".repeat(1_000_000), Map.of());

    validator.validateInput(mockRequestWithContentLength(-1), dslRequest, "P");
  }

  @Test
  void disablesInputCapWhenMaxIsHuge() throws Exception {
    DslRunsProperties properties = new DslRunsProperties();
    properties.setMaxInputBytes(Long.MAX_VALUE);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(objectMapper, properties);

    DslRequest dslRequest = new DslRequest("a".repeat(1_000_000), Map.of());

    validator.validateInput(mockRequestWithContentLength(-1), dslRequest, "P");
  }

  @Test
  void rejectsBasedOnContentLengthBeforeDeserialization() {
    DslRunsProperties properties = new DslRunsProperties();
    properties.setMaxInputBytes(50L);
    DslPayloadSizeValidator validator = new DslPayloadSizeValidator(objectMapper, properties);

    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    when(httpHeaders.getContentLength()).thenReturn(100L);
    ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
    when(headers.asHttpHeaders()).thenReturn(httpHeaders);
    ServerRequest request = mock(ServerRequest.class);
    when(request.headers()).thenReturn(headers);

    assertThatThrownBy(() -> validator.validateInput(request, new DslRequest("x", Map.of()), "P"))
            .isInstanceOf(DslPayloadTooLargeException.class)
            .satisfies(ex -> {
              DslPayloadTooLargeException e = (DslPayloadTooLargeException) ex;
              assertThat(e.getLimit()).isEqualTo(50L);
              assertThat(e.getActualBytes()).isEqualTo(100L);
            });
  }

  private static ServerRequest mockRequestWithContentLength(long contentLength) {
    HttpHeaders httpHeaders = mock(HttpHeaders.class);
    when(httpHeaders.getContentLength()).thenReturn(contentLength);
    ServerRequest.Headers headers = mock(ServerRequest.Headers.class);
    when(headers.asHttpHeaders()).thenReturn(httpHeaders);
    ServerRequest request = mock(ServerRequest.class);
    when(request.headers()).thenReturn(headers);
    return request;
  }
}
