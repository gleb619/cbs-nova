package cbs.nova.starter.web;

import cbs.nova.starter.config.properties.CbsSecurityRateLimitProperties;
import cbs.nova.starter.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class RateLimitFilter extends OncePerRequestFilter {

  private static final String X_FORWARDED_FOR_HEADER = "X-Forwarded-For";
  private static final String RETRY_AFTER_HEADER = "Retry-After";
  private static final String RATE_LIMITED_CODE = "RATE_LIMITED";
  private static final String RATE_LIMITED_MESSAGE = "Rate limit exceeded. Retry after the indicated delay.";
  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private static final List<RateLimitRule> RULES = List.of(
          new RateLimitRule("POST", "/api/dsl/run/**"),
          new RateLimitRule("POST", "/api/dsl/preview/**"),
          new RateLimitRule("POST", "/api/dsl/explain/**"),
          new RateLimitRule("POST", "/api/dsl/reload"),
          new RateLimitRule("POST", "/api/dsl/drafts/*/save"),
          new RateLimitRule("POST", "/api/dsl/drafts/*/publish"),
          new RateLimitRule("DELETE", "/api/dsl/drafts/*"),
          new RateLimitRule("POST", "/api/executions/*/cancel"));

  private final CbsSecurityRateLimitProperties properties;
  private final ObjectMapper objectMapper;
  private final LongSupplier nanoTime;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public RateLimitFilter(CbsSecurityRateLimitProperties properties, ObjectMapper objectMapper) {
    this(properties, objectMapper, System::nanoTime);
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    if (!properties.enabled() || !shouldRateLimit(request)) {
      filterChain.doFilter(request, response);
      return;
    }
    Consumption consumption = consume(clientIp(request));
    if (consumption.allowed()) {
      filterChain.doFilter(request, response);
      return;
    }
    response.setStatus(429);
    response.setHeader(RETRY_AFTER_HEADER, String.valueOf(consumption.retryAfterSeconds()));
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(RATE_LIMITED_CODE, RATE_LIMITED_MESSAGE, null, null, null));
  }

  private boolean shouldRateLimit(HttpServletRequest request) {
    if ("GET".equalsIgnoreCase(request.getMethod())) {
      return false;
    }
    String path = request.getRequestURI();
    return RULES.stream()
            .anyMatch(rule -> rule.method().equalsIgnoreCase(request.getMethod())
                    && pathMatcher.match(rule.pattern(), path));
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

  private Consumption consume(String clientIp) {
    long now = nanoTime.getAsLong();
    Bucket bucket = buckets.compute(clientIp, (ip, current) -> {
      Bucket baseline = current == null ? new Bucket(properties.capacity(), now, false) : current;
      long elapsedNanos = now - baseline.lastRefillNanos();
      double refill = elapsedNanos * properties.refillPerSecond() / NANOS_PER_SECOND;
      double tokens = Math.min(properties.capacity(), baseline.tokens() + refill);
      if (tokens >= 1.0) {
        return new Bucket(tokens - 1.0, now, true);
      }
      return new Bucket(tokens, now, false);
    });
    return bucket.toConsumption(properties.refillPerSecond());
  }

  private record RateLimitRule(String method, String pattern) {
  }

  private record Bucket(double tokens, long lastRefillNanos, boolean consumed) {

    Consumption toConsumption(double refillPerSecond) {
      if (consumed) {
        return new Consumption(true, 0L);
      }
      long retryAfterSeconds = Math.max(1L,
              (long) Math.ceil((1.0 - tokens) / refillPerSecond));
      return new Consumption(false, retryAfterSeconds);
    }
  }

  private record Consumption(boolean allowed, long retryAfterSeconds) {
  }
}
