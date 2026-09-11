package cbs.nova.starter.service;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.persistence.DslAuditRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.function.ServerRequest;
import tools.jackson.databind.ObjectMapper;

/**
 * Observability-side writer for the append-only {@code dsl_audit} log.
 *
 * <p>
 * {@link #record} is deliberately fail-safe: any failure (serialization, JDBC, anything else) is
 * logged at warn level and swallowed. Audit logging must never break or roll back the control-plane
 * mutation it observes — it is not a transaction participant.
 */
@Slf4j
@RequiredArgsConstructor
public class DslAuditService {

  private static final String ANONYMOUS = StarterConstants.ANONYMOUS_PRINCIPAL;

  private final DslAuditRepository repository;
  private final ObjectMapper objectMapper;

  /**
   * Appends one audit row. {@code details} is serialized to JSON with the shared
   * {@link ObjectMapper}; {@code null} details is stored as NULL.
   */
  public void record(String actor, String action, String target, @Nullable String correlationId,
          String outcome, @Nullable Object details) {
    try {
      String detailsJson = details != null ? objectMapper.writeValueAsString(details) : null;
      repository.insert(new DslAuditEntity(null, Instant.now(), actor, action, target,
              correlationId, outcome, detailsJson));
    } catch (Exception e) {
      log.warn("[DSL audit] failed to record {} on target '{}': {}", action, target,
              e.getMessage());
    }
  }

  /**
   * Resolves the acting principal for an audit row from the Spring Security context: the
   * authenticated principal name when OIDC/JWT auth is in force, {@code "anonymous"} otherwise (the
   * default permit-all chain, or the API-key filter, which validates the key but does not populate
   * an {@link Authentication}).
   */
  public static String currentActor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)) {
      String name = auth.getName();
      if (name != null && !name.isBlank()) {
        return name;
      }
    }
    return ANONYMOUS;
  }

  /**
   * Resolves the caller-supplied {@code X-Correlation-Id} header for an audit row, reusing the
   * {@link CorrelationId} validation rules. Returns {@code null} when the header is absent (the
   * server never fabricates one) or violates the validation rules.
   */
  public static @Nullable String correlationIdOf(ServerRequest request) {
    try {
      return CorrelationId.validated(
              request.headers().firstHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }
}
