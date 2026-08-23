package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.models.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

class ApiKeyAuthFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void nullKeyDisablesEnforcement() throws ServletException, IOException {
    assertPassthrough(null);
  }

  @Test
  void blankKeyDisablesEnforcement() throws ServletException, IOException {
    assertPassthrough("   ");
  }

  @Test
  void correctHeaderPassesThroughWithoutWritingError() throws ServletException, IOException {
    CallTracker tracker = new CallTracker();
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "secret-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", objectMapper)
            .doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getContentAsString()).isEmpty();
    assertThat(response.getStatus()).isNotEqualTo(401);
  }

  @Test
  void missingHeaderRejectedWith401AndShortCircuits() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).contains("application/json");
    ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(body.code()).isEqualTo("UNAUTHORIZED");
    assertThat(body.message()).contains("X-Api-Key");
  }

  @Test
  void wrongHeaderRejectedWith401AndShortCircuits() throws ServletException, IOException {
    MockHttpServletRequest request = new MockHttpServletRequest();
    request.addHeader(ApiKeyAuthFilter.API_KEY_HEADER, "wrong-key");
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter("secret-key", objectMapper)
            .doFilterInternal(request, response, chainThatFailsIfInvoked());

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).contains("application/json");
    ErrorResponse body = objectMapper.readValue(response.getContentAsString(), ErrorResponse.class);
    assertThat(body.code()).isEqualTo("UNAUTHORIZED");
  }

  private static void assertPassthrough(String configuredKey) throws ServletException, IOException {
    CallTracker tracker = new CallTracker();
    MockHttpServletResponse response = new MockHttpServletResponse();

    new ApiKeyAuthFilter(configuredKey, new ObjectMapper())
            .doFilterInternal(new MockHttpServletRequest(), response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getContentAsString()).isEmpty();
    assertThat(response.getStatus()).isNotEqualTo(401);
  }

  private static FilterChain chainThatFailsIfInvoked() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        throw new AssertionError("X-Api-Key filter must short-circuit and not invoke the chain");
      }
    };
  }

  private static final class CallTracker {

    private boolean called;

    FilterChain chain() {
      return new FilterChain() {
        @Override
        public void doFilter(ServletRequest request, ServletResponse response) {
          called = true;
        }
      };
    }
  }
}