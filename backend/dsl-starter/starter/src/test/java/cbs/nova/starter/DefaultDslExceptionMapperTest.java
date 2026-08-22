package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.error.DefaultDslExceptionMapper;
import cbs.nova.starter.error.DslExceptionMapper;
import cbs.nova.starter.models.ErrorResponse;
import tools.jackson.databind.ObjectMapper;
import io.sentry.Sentry;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;

class DefaultDslExceptionMapperTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  private final DslExceptionMapper mapper = new DefaultDslExceptionMapper();

  @Test
  void dslExceptionProducesExactJsonBody() throws Exception {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<ErrorResponse> response = mapper.handle(
            new DslException("run-abc", DslErrorCode.EXECUTION_FAILED, "dsl failed"),
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    String body = JSON.writeValueAsString(response.getBody());
    assertThat(body).isEqualTo(
            "{\"code\":\"EXECUTION_FAILED\","
                    + "\"message\":\"dsl failed\","
                    + "\"entityName\":null,"
                    + "\"runId\":\"run-abc\","
                    + "\"exceptionId\":\"" + response.getBody().exceptionId() + "\"}");
  }

  @Test
  void illegalArgumentExceptionProducesExactJsonBody() throws Exception {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<ErrorResponse> response = mapper.handle(new IllegalArgumentException("bad arg"),
            request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    String body = JSON.writeValueAsString(response.getBody());
    assertThat(body).isEqualTo(
            "{\"code\":\"BAD_REQUEST\","
                    + "\"message\":\"bad arg\","
                    + "\"entityName\":null,"
                    + "\"runId\":null,"
                    + "\"exceptionId\":null}");
  }

  @Test
  void generalExceptionProducesExactJsonBody() throws Exception {
    WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

    ResponseEntity<ErrorResponse> response = mapper.handle(new RuntimeException("boom"), request);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    String body = JSON.writeValueAsString(response.getBody());
    assertThat(body).isEqualTo(
            "{\"code\":\"INTERNAL_ERROR\","
                    + "\"message\":\"boom\","
                    + "\"entityName\":null,"
                    + "\"runId\":null,"
                    + "\"exceptionId\":null}");
  }

  @Test
  void dslExceptionSentryIsTaggedWithRunId() {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

      mapper.handle(new DslException("run-abc", DslErrorCode.EXECUTION_FAILED, "dsl failed"),
              request);

      sentry.verify(() -> Sentry.setTag("runId", "run-abc"));
      sentry.verify(() -> Sentry.captureException(Mockito.any(DslException.class)));
    }
  }

  @Test
  void generalExceptionSentryUsesRequestAttributeRunId() {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      WebRequest request = mock(WebRequest.class);
      when(request.getAttribute("runId", WebRequest.SCOPE_REQUEST)).thenReturn("run-req");

      mapper.handle(new RuntimeException("boom"), request);

      sentry.verify(() -> Sentry.setTag("runId", "run-req"));
      sentry.verify(() -> Sentry.captureException(Mockito.any(RuntimeException.class)));
    }
  }

  @Test
  void generalExceptionSentryRunsEvenWithoutRunId() {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

      mapper.handle(new RuntimeException("boom"), request);

      sentry.verify(() -> Sentry.captureException(Mockito.any(RuntimeException.class)));
      sentry.verify(() -> Sentry.setTag(Mockito.anyString(), Mockito.anyString()), Mockito.never());
    }
  }

  @Test
  void sentryUnconfiguredDoesNotBreakRequest() {
    try (MockedStatic<Sentry> sentry = Mockito.mockStatic(Sentry.class)) {
      sentry.when(() -> Sentry.captureException(Mockito.any(Exception.class)))
              .thenThrow(new IllegalStateException("sdk not initialised"));
      WebRequest request = new ServletWebRequest(new MockHttpServletRequest());

      ResponseEntity<ErrorResponse> response = mapper.handle(new RuntimeException("boom"), request);

      assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }
}
