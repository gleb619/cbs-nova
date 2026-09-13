package cbs.nova.starter.web;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.service.ApiKeyStore;
import cbs.nova.starter.service.ApiKeyStore.StoredKeyMatch;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;


@Slf4j
public final class ApiKeyAuthFilter extends OncePerRequestFilter {

  private final @Nullable String configuredApiKey;
  private final @Nullable ApiKeyStore apiKeyStore;
  private final ObjectMapper objectMapper;
  private final AtomicBoolean deprecationLogged = new AtomicBoolean(false);

  // TODO: replace with lombok's constructor and spring config class
  public ApiKeyAuthFilter(@Nullable String configuredApiKey,
          @Nullable ApiKeyStore apiKeyStore,
          ObjectMapper objectMapper) {
    this.configuredApiKey = (configuredApiKey != null && !configuredApiKey.isBlank())
            ? configuredApiKey
            : null;
    this.apiKeyStore = apiKeyStore;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    String headerValue = request.getHeader(StarterConstants.API_KEY_HEADER);

    if (headerValue == null || headerValue.isBlank()) {
      if (authRequired()) {
        writeUnauthorized(response, "Missing X-Api-Key");
        return;
      }
      filterChain.doFilter(request, response);
      return;
    }

    if (configuredApiKey != null
            && MessageDigest.isEqual(headerValue.getBytes(StandardCharsets.UTF_8),
                    configuredApiKey.getBytes(StandardCharsets.UTF_8))) {
      if (deprecationLogged.compareAndSet(false, true)) {
        log
                .warn("cbs.dsl.auth.api-key (property) authenticated a request — this bootstrap "
                        + "key is deprecated; rotate to a stored key via "
                        + "POST /api/dsl/auth/keys and remove the property");
      }
      filterChain.doFilter(request, response);
      return;
    }

    if (apiKeyStore != null) {
      Optional<StoredKeyMatch> match = apiKeyStore.matches(headerValue);
      if (match.isPresent()) {
        apiKeyStore.touchLastUsed(match.get().id());
        filterChain.doFilter(request, response);
        return;
      }
    }

    writeUnauthorized(response, "Invalid X-Api-Key");
  }


  private boolean authRequired() {
    return configuredApiKey != null || apiKeyStore != null;
  }

  private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse("UNAUTHORIZED", message, null, null, null, null));
  }
}
