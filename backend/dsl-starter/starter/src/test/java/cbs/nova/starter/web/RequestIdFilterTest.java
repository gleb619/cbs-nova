package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class RequestIdFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private final RequestIdFilter filter = new RequestIdFilter(objectMapper);

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void propagatesIncomingRequestIdToMdcAndResponse() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Request-Id", "req-abc");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chainThatCapturesMdc());

    assertThat(capturedMdc.get("requestId")).isNull();
    assertThat(response.getHeader("X-Request-Id")).isEqualTo("req-abc");
  }

  @Test
  void generatesRequestIdWhenHeaderMissing() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chainThatCapturesMdc());

    assertThat(response.getHeader("X-Request-Id")).isNotBlank();
  }

  @Test
  void putsValidCorrelationIdIntoMdcDuringRequestOnly() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "order-4711");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chainThatCapturesMdc());

    assertThat(capturedMdc.get(StarterConstants.CORRELATION_ID_MDC_KEY)).isEqualTo("order-4711");
    assertThat(MDC.get(StarterConstants.CORRELATION_ID_MDC_KEY)).isNull();
    assertThat(MDC.get(StarterConstants.REQUEST_ID_MDC_KEY)).isNull();
  }

  @Test
  void absentCorrelationIdLeavesNoCidInMdc() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chainThatCapturesMdc());

    assertThat(capturedMdc.containsKey(StarterConstants.CORRELATION_ID_MDC_KEY)).isFalse();
    assertThat(MDC.get(StarterConstants.CORRELATION_ID_MDC_KEY)).isNull();
  }

  @Test
  void invalidCorrelationIdRejectedWith400AndShortCircuits() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader("X-Correlation-Id", "bad id!");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(400);
    assertThat(response.getContentType()).contains("application/json");
    ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(body.getCode()).isEqualTo("INVALID_CORRELATION_ID");
  }

  private final Map<String, String> capturedMdc = new HashMap<>();

  private FilterChain chainThatCapturesMdc() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        capturedMdc.putAll(MDC.getCopyOfContextMap() == null
                ? Map.of()
                : MDC.getCopyOfContextMap());
      }
    };
  }

  private static FilterChain chainThatFailsIfInvoked() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        throw new AssertionError(
                "RequestIdFilter must short-circuit and not invoke the chain on an invalid"
                        + " X-Correlation-Id");
      }
    };
  }
}
