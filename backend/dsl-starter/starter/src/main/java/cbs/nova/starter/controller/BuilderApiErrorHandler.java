package cbs.nova.starter.controller;

import cbs.nova.dsl.BuilderErrorResponse;
import cbs.nova.starter.exception.BuilderApiException;
import cbs.nova.starter.exception.BuilderClientBusyException;
import cbs.nova.starter.exception.BuilderUnavailableException;
import cbs.nova.starter.exception.DslCompilationException;
import cbs.nova.starter.model.CompileDiagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.StreamUtils;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class BuilderApiErrorHandler {

  private final ObjectMapper objectMapper;

  public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
    String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    HttpStatusCode status = response.getStatusCode();
    BuilderErrorResponse error = parse(body);
    String code = error.primaryCode();
    String message = error.primaryMessage();
    if (status.value() == 429) {
      throw new BuilderClientBusyException(message);
    }
    if (status.value() == 422) {
      throw new DslCompilationException(message, diagnostics(error));
    }
    if (status.is5xxServerError()) {
      throw new BuilderUnavailableException(message);
    }
    throw new BuilderApiException(status, code, message);
  }

  private BuilderErrorResponse parse(String body) {
    if (body == null || body.isBlank()) {
      return new BuilderErrorResponse(null, null, null, null);
    }
    try {
      return objectMapper.readValue(body, BuilderErrorResponse.class);
    } catch (Exception e) {
      return new BuilderErrorResponse(null, body, null, null);
    }
  }

  private List<CompileDiagnostic> diagnostics(BuilderErrorResponse error) {
    List<String> values = error.diagnostics();
    if (values.isEmpty()) {
      return List.of();
    }
    List<CompileDiagnostic> diagnostics = new ArrayList<>(values.size());
    for (String value : values) {
      diagnostics.add(new CompileDiagnostic(null, null, null, value, "error", null));
    }
    return diagnostics;
  }

}
