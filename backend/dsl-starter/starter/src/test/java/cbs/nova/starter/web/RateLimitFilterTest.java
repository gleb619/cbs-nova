package cbs.nova.starter.web;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

class RateLimitFilterTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void disabledByDefaultPassesThroughWithoutBehaviorChange() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(disabledProperties(), objectMapper);
    MockHttpServletRequest request = post("/api/dsl/run/demo");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CallTracker tracker = new CallTracker();

    filter.doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(response.getContentAsString()).isEmpty();
  }

  @Test
  void exemptGetRequestPassesThrough() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(2, 1.0), objectMapper);
    MockHttpServletRequest request = get("/api/dsl/run/demo");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CallTracker tracker = new CallTracker();

    filter.doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void exemptActuatorHealthPassesThrough() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(2, 1.0), objectMapper);
    MockHttpServletRequest request = get("/actuator/health");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CallTracker tracker = new CallTracker();

    filter.doFilterInternal(request, response, tracker.chain());

    assertThat(tracker.called).isTrue();
    assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  @Test
  void bucketExhaustsAfterCapacityRequests() throws Exception {
    int capacity = 3;
    AtomicLong clock = new AtomicLong(0L);
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(capacity, 1.0), objectMapper,
            clock::get);

    for (int i = 0; i < capacity; i++) {
      assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isTrue();
    }
    assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isFalse();
  }

  @Test
  void bucketRefillsCorrectlyOverSimulatedTime() throws Exception {
    int capacity = 2;
    AtomicLong clock = new AtomicLong(0L);
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(capacity, 1.0), objectMapper,
            clock::get);

    assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isTrue();
    assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isTrue();
    assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isFalse();

    clock.addAndGet(1_000_000_000L);
    assertThat(doPost(filter, "/api/dsl/run/demo", "192.168.1.1")).isTrue();
  }

  @Test
  void perIpIsolationOneExhaustedBucketDoesNotAffectOther() throws Exception {
    AtomicLong clock = new AtomicLong(0L);
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(1, 1.0), objectMapper,
            clock::get);

    assertThat(doPost(filter, "/api/dsl/run/demo", "10.0.0.1")).isTrue();
    assertThat(doPost(filter, "/api/dsl/run/demo", "10.0.0.2")).isTrue();
    assertThat(doPost(filter, "/api/dsl/run/demo", "10.0.0.1")).isFalse();
  }

  @Test
  void rejectedRequestReturns429WithRetryAfterAndErrorBody() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(1, 1.0), objectMapper);

    MockHttpServletRequest first = post("/api/dsl/preview/demo");
    MockHttpServletResponse firstResponse = new MockHttpServletResponse();
    filter.doFilterInternal(first, firstResponse, noOpChain());
    assertThat(firstResponse.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    MockHttpServletRequest second = post("/api/dsl/preview/demo");
    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilterInternal(second, secondResponse, chainThatFailsIfInvoked());

    assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
    assertThat(secondResponse.getHeader("Retry-After")).isEqualTo("1");
    assertThat(secondResponse.getContentType()).contains("application/json");
    ErrorResponse body = objectMapper.readValue(secondResponse.getContentAsString(),
            ErrorResponse.class);
    assertThat(body.code()).isEqualTo("RATE_LIMITED");
    assertThat(body.message()).isNotBlank();
  }

  @Test
  void xForwardedForFirstIpIsUsedAsBucketKey() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(enabledProperties(1, 1.0), objectMapper);

    MockHttpServletRequest request = post("/api/dsl/run/demo");
    request.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18, 150.172.238.178");
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilterInternal(request, response, noOpChain());
    assertThat(response.getStatus()).isNotEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());

    MockHttpServletRequest second = post("/api/dsl/run/demo");
    second.addHeader("X-Forwarded-For", "203.0.113.1, 70.41.3.18");
    MockHttpServletResponse secondResponse = new MockHttpServletResponse();
    filter.doFilterInternal(second, secondResponse, chainThatFailsIfInvoked());
    assertThat(secondResponse.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
  }

  private static boolean doPost(RateLimitFilter filter, String path, String remoteAddr)
          throws Exception {
    MockHttpServletRequest request = post(path);
    request.setRemoteAddr(remoteAddr);
    MockHttpServletResponse response = new MockHttpServletResponse();
    filter.doFilterInternal(request, response, noOpChain());
    return response.getStatus() != HttpStatus.TOO_MANY_REQUESTS.value();
  }

  private static CbsSecurityRateLimitProperties disabledProperties() {
    return new CbsSecurityRateLimitProperties(false, 20, 5.0);
  }

  private static CbsSecurityRateLimitProperties enabledProperties(int capacity,
          double refillPerSecond) {
    return new CbsSecurityRateLimitProperties(true, capacity, refillPerSecond);
  }

  private static MockHttpServletRequest post(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
    request.setRequestURI(path);
    return request;
  }

  private static MockHttpServletRequest get(String path) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
    request.setRequestURI(path);
    return request;
  }

  private static FilterChain noOpChain() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        // intentionally empty
      }
    };
  }

  private static FilterChain chainThatFailsIfInvoked() {
    return new FilterChain() {
      @Override
      public void doFilter(ServletRequest request, ServletResponse response) {
        throw new AssertionError("Filter must short-circuit and not invoke the chain");
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
