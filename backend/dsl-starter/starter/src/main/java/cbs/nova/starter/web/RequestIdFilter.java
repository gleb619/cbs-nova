package cbs.nova.starter.web;

import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.CorrelationId;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.UUID;

public final class RequestIdFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";
  public static final String REQUEST_ID_MDC_KEY = "rid";
  public static final String CORRELATION_ID_MDC_KEY = "cid";

  private static final String INVALID_CORRELATION_ID_CODE = "INVALID_CORRELATION_ID";
  private static final String INVALID_CORRELATION_ID_MESSAGE = "Invalid X-Correlation-Id header";

  private final ObjectMapper objectMapper;

  public RequestIdFilter() {
    this(new ObjectMapper());
  }

  public RequestIdFilter(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    String correlationId;
    try {
      correlationId = CorrelationId.validated(
              request.getHeader(CorrelationId.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(),
              new ErrorResponse(INVALID_CORRELATION_ID_CODE, INVALID_CORRELATION_ID_MESSAGE,
                      null, null, null));
      return;
    }
    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    if (correlationId != null) {
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
    }
    response.setHeader(REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID_MDC_KEY);
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }
}
