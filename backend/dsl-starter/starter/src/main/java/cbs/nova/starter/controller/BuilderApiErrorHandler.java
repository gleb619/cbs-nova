package cbs.nova.starter.controller;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class BuilderApiErrorHandler {

  private final ObjectMapper objectMapper;

  public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
    String body = StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
    HttpStatusCode status = response.getStatusCode();
    JsonNode node = parse(body);
    String code = firstNonBlank(text(node, "code"), text(node, "error"), "BUILDER_ERROR");
    String message = firstNonBlank(text(node, "message"), status.toString());
    if (status.value() == 429) {
      throw new BuilderClientBusyException(message);
    }
    if (status.value() == 422) {
      throw new DslCompilationException(message, diagnostics(node));
    }
    if (status.is5xxServerError()) {
      throw new BuilderUnavailableException(message);
    }
    throw new BuilderApiException(status, code, message);
  }

  private JsonNode parse(String body) {
    if (body == null || body.isBlank()) {
      return null;
    }
    try {
      return objectMapper.readTree(body);
    } catch (Exception e) {
      return null;
    }
  }

  private List<CompileDiagnostic> diagnostics(JsonNode node) {
    JsonNode values = node == null ? null : node.get("diagnostics");
    if (values == null || !values.isArray()) {
      return List.of();
    }
    List<CompileDiagnostic> diagnostics = new ArrayList<>();
    for (JsonNode value : values) {
      if (value.isString()) {
        diagnostics.add(new CompileDiagnostic(null, null, null, value.asString(), "error", null));
      }
    }
    return diagnostics;
  }

  private String text(JsonNode node, String field) {
    JsonNode value = node == null ? null : node.get(field);
    return value != null && value.isString() ? value.asString() : null;
  }

  private String firstNonBlank(String... candidates) {
    for (String candidate : candidates) {
      if (candidate != null && !candidate.isBlank()) {
        return candidate;
      }
    }
    return "builder request failed";
  }

}
