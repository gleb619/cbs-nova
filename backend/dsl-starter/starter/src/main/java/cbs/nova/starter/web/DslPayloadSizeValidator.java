package cbs.nova.starter.web;

import cbs.nova.starter.config.properties.DslRunsProperties;
import cbs.nova.starter.exception.DslPayloadTooLargeException;
import cbs.nova.starter.model.DslRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DslPayloadSizeValidator {

  private final ObjectMapper objectMapper;
  private final DslRunsProperties properties;

  public void validateInput(ServerRequest request, DslRequest dslRequest, String entityName) {
    long limit = effectiveLimit(properties.getMaxInputBytes());
    if (limit == Long.MAX_VALUE) {
      return;
    }

    long contentLength = request.headers().asHttpHeaders().getContentLength();
    if (contentLength >= 0 && contentLength > limit) {
      throw new DslPayloadTooLargeException(limit, contentLength, entityName);
    }

    long actualBytes = serializedSize(dslRequest);
    if (actualBytes > limit) {
      throw new DslPayloadTooLargeException(limit, actualBytes, entityName);
    }
  }

  private long effectiveLimit(long configured) {
    return configured <= 0 ? Long.MAX_VALUE : configured;
  }

  private long serializedSize(DslRequest dslRequest) {
    try {
      return objectMapper.writeValueAsBytes(dslRequest).length;
    } catch (Exception ex) {
      throw new IllegalStateException("Failed to measure request body size", ex);
    }
  }
}
