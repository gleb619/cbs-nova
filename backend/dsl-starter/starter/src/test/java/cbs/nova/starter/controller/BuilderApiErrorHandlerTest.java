package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.BuilderErrorResponse;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.exception.DslCompilationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import tools.jackson.databind.ObjectMapper;

class BuilderApiErrorHandlerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final BuilderApiErrorHandler handler = new BuilderApiErrorHandler(objectMapper);

  @Test
  void status429ThrowsBuilderClientBusyExceptionWithMessage() throws IOException {
    ClientHttpResponse response = response(429, "{\"message\":\"rate limited\"}");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOf(BuilderClientBusyException.class)
            .hasMessage("rate limited");
  }

  @Test
  void status422ThrowsDslCompilationExceptionWithDiagnostics() throws IOException {
    ClientHttpResponse response = response(422,
            "{\"message\":\"compile failed\",\"diagnostics\":[\"first error\",\"second error\"]}");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOfSatisfying(DslCompilationException.class, ex -> {
              assertThat(ex.getMessage()).isEqualTo("compile failed");
              assertThat(ex.diagnostics()).hasSize(2);
              assertThat(ex.diagnostics().get(0).message()).isEqualTo("first error");
              assertThat(ex.diagnostics().get(0).severity()).isEqualTo("error");
              assertThat(ex.diagnostics().get(0).file()).isNull();
              assertThat(ex.diagnostics().get(0).line()).isNull();
              assertThat(ex.diagnostics().get(0).column()).isNull();
              assertThat(ex.diagnostics().get(1).message()).isEqualTo("second error");
              assertThat(ex.diagnostics().get(1).severity()).isEqualTo("error");
            });
  }

  @Test
  void status422WithEmptyDiagnosticsReturnsEmptyList() throws IOException {
    ClientHttpResponse response = response(422,
            "{\"message\":\"compile failed\",\"diagnostics\":[]}");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOfSatisfying(DslCompilationException.class,
                    ex -> assertThat(ex.diagnostics()).isEmpty());
  }

  @Test
  void status5xxThrowsBuilderUnavailableException() throws IOException {
    ClientHttpResponse response = response(503, "{\"message\":\"builder down\"}");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOf(BuilderUnavailableException.class)
            .hasMessage("builder down");
  }

  @Test
  void nonMatchingStatusThrowsBuilderApiExceptionWithCodeAndStatus() throws IOException {
    ClientHttpResponse response = response(400,
            "{\"code\":\"BAD_REQUEST\",\"message\":\"invalid request\"}");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getStatusCode().value()).isEqualTo(400);
              assertThat(ex.getCode()).isEqualTo("BAD_REQUEST");
              assertThat(ex.getMessage()).isEqualTo("invalid request");
            });
  }

  @Test
  void blankBodyProducesFallbackCodeAndMessage() throws IOException {
    ClientHttpResponse response = response(400, "   ");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOfSatisfying(BuilderApiException.class, ex -> {
              assertThat(ex.getCode()).isEqualTo("BUILDER_ERROR");
              assertThat(ex.getMessage()).isEqualTo("builder request failed");
            });
  }

  @Test
  void malformedBodyFallsBackToRawBodyAsMessage() throws IOException {
    ClientHttpResponse response = response(400, "not json");

    assertThatThrownBy(() -> handler.handle(mock(HttpRequest.class), response))
            .isInstanceOfSatisfying(BuilderApiException.class,
                    ex -> assertThat(ex.getMessage()).isEqualTo("not json"));
  }

  @Test
  void parseNullBodyReturnsAllNullBuilderErrorResponse() throws Exception {
    BuilderErrorResponse parsed = invokeParse(null);

    assertThat(parsed.code()).isNull();
    assertThat(parsed.message()).isNull();
    assertThat(parsed.error()).isNull();
    assertThat(parsed.diagnostics()).isEmpty();
  }

  @Test
  void parseBlankBodyReturnsAllNullBuilderErrorResponse() throws Exception {
    BuilderErrorResponse parsed = invokeParse("   ");

    assertThat(parsed.code()).isNull();
    assertThat(parsed.message()).isNull();
    assertThat(parsed.error()).isNull();
    assertThat(parsed.diagnostics()).isEmpty();
  }

  @Test
  void parseValidJsonReturnsParsedBuilderErrorResponse() throws Exception {
    BuilderErrorResponse parsed = invokeParse(
            "{\"code\":\"C\",\"message\":\"M\",\"error\":\"E\",\"diagnostics\":[\"D\"]}");

    assertThat(parsed.code()).isEqualTo("C");
    assertThat(parsed.message()).isEqualTo("M");
    assertThat(parsed.error()).isEqualTo("E");
    assertThat(parsed.diagnostics()).containsExactly("D");
  }

  @Test
  void parseMalformedJsonReturnsRawBodyAsMessage() throws Exception {
    BuilderErrorResponse parsed = invokeParse("raw");

    assertThat(parsed.code()).isNull();
    assertThat(parsed.message()).isEqualTo("raw");
    assertThat(parsed.error()).isNull();
    assertThat(parsed.diagnostics()).isEmpty();
  }

  private ClientHttpResponse response(int status, String body) throws IOException {
    ClientHttpResponse response = mock(ClientHttpResponse.class);
    when(response.getStatusCode()).thenReturn(HttpStatusCode.valueOf(status));
    when(response.getBody())
            .thenReturn(new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)));
    return response;
  }

  private BuilderErrorResponse invokeParse(String body) throws Exception {
    Method method = BuilderApiErrorHandler.class.getDeclaredMethod("parse", String.class);
    method.setAccessible(true);
    return (BuilderErrorResponse) method.invoke(handler, body);
  }
}
