package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.io.IOException;

class RequestIdFilterTest {

  private final RequestIdFilter filter = new RequestIdFilter();

  @AfterEach
  void tearDown() {
    MDC.clear();
  }

  @Test
  void propagatesIncomingRequestIdToMdcAndResponse() throws ServletException, IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader("X-Request-Id")).thenReturn("req-abc");

    filter.doFilterInternal(request, response, chain);

    assertThat(MDC.get("requestId")).isNull();
    org.mockito.Mockito.verify(response).setHeader("X-Request-Id", "req-abc");
  }

  @Test
  void generatesRequestIdWhenHeaderMissing() throws ServletException, IOException {
    HttpServletRequest request = mock(HttpServletRequest.class);
    HttpServletResponse response = mock(HttpServletResponse.class);
    FilterChain chain = mock(FilterChain.class);
    when(request.getHeader("X-Request-Id")).thenReturn(null);

    filter.doFilterInternal(request, response, chain);

    org.mockito.Mockito.verify(response)
            .setHeader(org.mockito.ArgumentMatchers.eq("X-Request-Id"),
                    org.mockito.ArgumentMatchers.argThat(
                            (String id) -> id != null && !id.isBlank()));
  }
}
