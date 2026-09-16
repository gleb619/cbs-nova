package cbs.nova.starter.web;

import static cbs.nova.starter.core.StarterConstants.INVALID_CORRELATION_ID_CODE;
import static cbs.nova.starter.core.StarterConstants.INVALID_CORRELATION_ID_MESSAGE;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.dsl.model.ErrorResponse;
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
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class RequestIdFilter extends OncePerRequestFilter {

  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    String correlationId;
    try {
      correlationId = CorrelationId.validated(
              request.getHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      response.setStatus(HttpStatus.BAD_REQUEST.value());
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(),
              new ErrorResponse(INVALID_CORRELATION_ID_CODE, INVALID_CORRELATION_ID_MESSAGE, null,
                      null, null, null, null, null, null));
      return;
    }
    String requestId = request.getHeader(StarterConstants.REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UUID.randomUUID().toString();
    }
    MDC.put(StarterConstants.REQUEST_ID_MDC_KEY, requestId);
    if (correlationId != null) {
      MDC.put(StarterConstants.CORRELATION_ID_MDC_KEY, correlationId);
    }
    response.setHeader(StarterConstants.REQUEST_ID_HEADER, requestId);
    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(StarterConstants.REQUEST_ID_MDC_KEY);
      MDC.remove(StarterConstants.CORRELATION_ID_MDC_KEY);
    }
  }
}
