package cbs.nova.starter.web;

import static cbs.nova.starter.core.StarterConstants.API_KEY_HEADER;
import static cbs.nova.starter.core.StarterConstants.RATE_LIMITED_CODE;
import static cbs.nova.starter.core.StarterConstants.RATE_LIMITED_MESSAGE;
import static cbs.nova.starter.core.StarterConstants.RETRY_AFTER_HEADER;
import static cbs.nova.starter.core.StarterConstants.X_FORWARDED_FOR_HEADER;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.ratelimit.Consumption;
import cbs.nova.starter.ratelimit.RateLimit;
import cbs.nova.starter.ratelimit.RateLimitStore;
import cbs.nova.starter.service.ApiKeyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public final class RateLimitFilter extends OncePerRequestFilter {

  private static final List<RateLimitRule> RULES = List.of(
          new RateLimitRule("POST", "/api/dsl/run/**"),
          new RateLimitRule("POST", "/api/dsl/preview/**"),
          new RateLimitRule("POST", "/api/dsl/explain/**"),
          new RateLimitRule("POST", "/api/dsl/reload"),
          new RateLimitRule("POST", "/api/dsl/drafts/*/save"),
          new RateLimitRule("POST", "/api/dsl/drafts/*/publish"),
          new RateLimitRule("POST", "/api/dsl/drafts/*/discard"),
          new RateLimitRule("DELETE", "/api/dsl/drafts/*"),
          new RateLimitRule("POST", "/api/executions/*/cancel"));

  private final CbsSecurityRateLimitProperties properties;
  private final ObjectMapper objectMapper;
  private final RateLimitStore store;
  private final @Nullable ApiKeyStore apiKeyStore;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    if (!properties.enabled() || !shouldRateLimit(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    String routeClass = matchedRouteClass(request);
    if (routeClass == null) {
      filterChain.doFilter(request, response);
      return;
    }
    String key = bucketKey(request, routeClass);
    Consumption consumption = store.consume(key, effectiveLimit(routeClass));
    if (consumption.allowed()) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader(RETRY_AFTER_HEADER, String.valueOf(consumption.retryAfterSeconds()));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(RATE_LIMITED_CODE, RATE_LIMITED_MESSAGE, null, null, null, null, null,
                    null, null));
  }

  private boolean shouldRateLimit(HttpServletRequest request) {
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    return matchedRouteClass(request) != null;
  }

  private @Nullable String matchedRouteClass(HttpServletRequest request) {
    String path = request.getRequestURI();
    String method = request.getMethod();
    for (RateLimitRule rule : RULES) {
      if (rule.method().equalsIgnoreCase(method) && pathMatcher.match(rule.pattern(), path)) {
        return rule.pattern();
      }
    }
    return null;
  }

  private String bucketKey(HttpServletRequest request, String routeClass) {
    return principal(request) + "|" + routeClass;
  }

  private String principal(HttpServletRequest request) {
    String authorization = request.getHeader("Authorization");
    if (authorization != null && authorization.startsWith("Bearer ")) {
      String sub = extractJwtSub(authorization.substring(7).trim());
      if (sub != null) {
        return "jwt:" + sub;
      }
    }
    String apiKey = request.getHeader(API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank() && apiKeyStore != null) {
      Optional<ApiKeyStore.StoredKeyMatch> match = apiKeyStore.matches(apiKey);
      if (match.isPresent()) {
        return "apikey:" + match.get().label();
      }
    }
    return "ip:" + clientIp(request);
  }

  private @Nullable String extractJwtSub(String token) {
    try {
      String[] parts = token.split("\\.");
      if (parts.length != 3) {
        return null;
      }
      String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
      JsonNode node = objectMapper.readTree(payload);
      JsonNode sub = node.get("sub");
      return sub != null && !sub.isNull() ? sub.asText() : null;
    } catch (RuntimeException e) {
      return null;
    }
  }

  private String clientIp(HttpServletRequest request) {
    String forwarded = request.getHeader(X_FORWARDED_FOR_HEADER);
    if (forwarded != null && !forwarded.isBlank()) {
      int comma = forwarded.indexOf(',');
      String first = comma < 0 ? forwarded : forwarded.substring(0, comma);
      return first.trim();
    }
    return request.getRemoteAddr();
  }

  private RateLimit effectiveLimit(String routeClass) {
    CbsSecurityRateLimitProperties.RateLimitClass override = properties.classes().get(routeClass);
    if (override != null) {
      return new RateLimit(override.capacity(), override.refillPerSecond());
    }
    return new RateLimit(properties.capacity(), properties.refillPerSecond());
  }

  private record RateLimitRule(String method, String pattern) {
  }
}
