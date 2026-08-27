package cbs.nova.starter.web;

import cbs.nova.starter.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public final class ApiKeyAuthFilter extends OncePerRequestFilter {

  public static final String API_KEY_HEADER = "X-Api-Key";

  private final String configuredApiKey;
  private final ObjectMapper objectMapper;

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    if (configuredApiKey == null || configuredApiKey.isBlank()) {
      filterChain.doFilter(request, response);
      return;
    }
    String headerValue = request.getHeader(API_KEY_HEADER);
    if (headerValue == null
            || !MessageDigest.isEqual(headerValue.getBytes(StandardCharsets.UTF_8),
                    configuredApiKey.getBytes(StandardCharsets.UTF_8))) {
      response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
      response.setContentType(MediaType.APPLICATION_JSON_VALUE);
      objectMapper.writeValue(response.getOutputStream(),
              new ErrorResponse("UNAUTHORIZED", "Invalid or missing X-Api-Key", null, null, null));
      return;
    }
    filterChain.doFilter(request, response);
  }
}
