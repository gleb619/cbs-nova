package cbs.nova.starter.security;

import static cbs.nova.starter.core.StarterConstants.ACTION_PIECE_GUARD_DENY;
import static cbs.nova.starter.core.StarterConstants.FORBIDDEN_CODE;
import static cbs.nova.starter.core.StarterConstants.OUTCOME_FAILURE;
import static cbs.nova.starter.core.StarterConstants.PIECE_BLOCKED_CODE;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.InvariantContext;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PreCheck;
import cbs.nova.starter.service.CorrelationId;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PieceCheckBlockRegistry;
import cbs.nova.starter.service.PieceCheckPipeline;
import cbs.nova.starter.service.PieceManifestService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet filter that enforces the {@code preCheck[]} of manifest pieces whose target is an
 * {@code api} route (T549). Runs on {@code /api/*}, ordered just after the principal-resolving
 * filters (see {@code PieceGuardFilterConfiguration}).
 *
 * <p>
 * The filter is strictly OPT-IN via manifest presence: when
 * {@link PieceManifestService#findByRoute} returns empty for a request the chain proceeds untouched
 * — no attribute is set, no response is written, no bucket is consumed. A route absent from the
 * manifest is byte-for-byte unaffected.
 *
 * <p>
 * For a matched piece, checks evaluate in manifest order; the first failure is governed by the
 * piece's {@code failMode}:
 * <ul>
 * <li>{@code deny} (default) — 403 with the unified {@link ErrorResponse} envelope
 * ({@code code = "FORBIDDEN"}, message naming the failed check type and piece id, {@code context}
 * carrying {@code pieceId} and {@code check}).</li>
 * <li>{@code audit-only} — a FAILURE {@code dsl_audit} row is written (when {@link DslAuditService}
 * is present, opportunistically) and the chain continues.</li>
 * </ul>
 *
 * <p>
 * On success two request attributes are set so the T550 post-check pipeline can reuse the
 * resolution without re-resolving:
 * <ul>
 * <li>{@value #PIECE_ID_ATTRIBUTE} — the matched piece id ({@link String}).</li>
 * <li>{@value #PRINCIPAL_ROLE_ATTRIBUTE} — the caller's resolved {@link Role} name
 * ({@link String}).</li>
 * </ul>
 *
 * <p>
 * Rate-class checks use a token bucket per (rate class, principal) owned by this guard. A rate
 * class named by the manifest but not configured under {@code cbs.dsl.manifest.rate-classes.*}
 * fails closed (denied) with a WARN log — a missing limiter is a misconfiguration, not an
 * exemption. These buckets are additive and independent of the global
 * {@code cbs.security.ratelimit.*} filter, which keeps its own per-client-IP buckets and route
 * list.
 */
@Slf4j
public final class PieceGuardFilter extends OncePerRequestFilter {

  /** Request attribute carrying the matched piece id after a successful pre-check. */
  public static final String PIECE_ID_ATTRIBUTE = "cbs.nova.piece.id";
  /**
   * Request attribute carrying the caller's resolved {@link Role} name after a successful
   * pre-check.
   */
  public static final String PRINCIPAL_ROLE_ATTRIBUTE = "cbs.nova.piece.principal-role";

  static final String FAIL_MODE_AUDIT_ONLY = "audit-only";

  private static final long NANOS_PER_SECOND = 1_000_000_000L;

  private final PieceManifestService manifestService;
  private final RoleResolver roleResolver;
  private final FeatureFlagSource flagSource;
  private final CbsDslManifestProperties properties;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final ObjectMapper objectMapper;
  private final LongSupplier nanoTime;
  private final @Nullable PieceCheckPipeline postCheckPipeline;
  private final @Nullable PieceCheckBlockRegistry blockRegistry;
  private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

  public PieceGuardFilter(
          PieceManifestService manifestService,
          RoleResolver roleResolver,
          FeatureFlagSource flagSource,
          CbsDslManifestProperties properties,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectMapper objectMapper,
          LongSupplier nanoTime) {
    this(manifestService, roleResolver, flagSource, properties, auditServiceProvider,
            objectMapper, nanoTime, null, null);
  }

  public PieceGuardFilter(
          PieceManifestService manifestService,
          RoleResolver roleResolver,
          FeatureFlagSource flagSource,
          CbsDslManifestProperties properties,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectMapper objectMapper,
          LongSupplier nanoTime,
          @Nullable PieceCheckPipeline postCheckPipeline,
          @Nullable PieceCheckBlockRegistry blockRegistry) {
    this.manifestService = manifestService;
    this.roleResolver = roleResolver;
    this.flagSource = flagSource;
    this.properties = properties;
    this.auditServiceProvider = auditServiceProvider;
    this.objectMapper = objectMapper;
    this.nanoTime = nanoTime;
    this.postCheckPipeline = postCheckPipeline;
    this.blockRegistry = blockRegistry;
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    Optional<Piece> found = manifestService.findByRoute(
            request.getMethod(), request.getRequestURI());
    if (found.isEmpty()) {
      filterChain.doFilter(request, response);
      return;
    }
    Piece piece = found.get();
    if (blockRegistry != null && blockRegistry.isBlocked(piece.id(), principalKey(request))) {
      denyBlocked(piece, request, response);
      return;
    }
    Role caller = roleResolver.resolve(request);
    for (PreCheck check : piece.preCheck()) {
      CheckFailure failure = evaluate(check, request, caller);
      if (failure != null) {
        onFailure(piece, failure, request, response, filterChain);
        return;
      }
    }
    request.setAttribute(PIECE_ID_ATTRIBUTE, piece.id());
    request.setAttribute(PRINCIPAL_ROLE_ATTRIBUTE, caller.name());
    proceedAndTriggerPostChecks(piece, caller, request, response, filterChain);
  }

  /**
   * Runs the rest of the chain and — only when it completes without exception and the response
   * status indicates success ({@code < 400}) — hands the piece to the post-check pipeline (T550).
   * Failed executions (handler error, non-2xx/3xx status) never trigger post-checks. The pipeline
   * submits asynchronously, so the response is never delayed.
   */
  private void proceedAndTriggerPostChecks(
          Piece piece,
          Role caller,
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    filterChain.doFilter(request, response);
    if (postCheckPipeline == null || piece.postCheck().isEmpty()) {
      return;
    }
    int status = response.getStatus();
    if (status >= 400) {
      log.debug("skipping postCheck for piece '{}' — response status {}", piece.id(), status);
      return;
    }
    postCheckPipeline.onSuccess(piece, principalKey(request), DslAuditService.currentActor(),
            correlationId(request),
            new InvariantContext(piece.id(), caller.name(), request.getMethod(),
                    request.getRequestURI(), status));
  }

  private void denyBlocked(
          Piece piece, HttpServletRequest request, HttpServletResponse response)
          throws IOException {
    log.warn("denying {} {} — piece '{}' is blocked by a failed post-check "
            + "(block-next-execution)", request.getMethod(), request.getRequestURI(), piece.id());
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(PIECE_BLOCKED_CODE,
                    "Piece '" + piece.id() + "' is blocked by a failed post-check "
                            + "(onFailure: block-next-execution) until the block is cleared",
                    null, null, null, null, null, null,
                    Map.of("pieceId", piece.id(), "check", "post-check-block")));
  }

  private @Nullable CheckFailure evaluate(PreCheck check, HttpServletRequest request, Role caller) {
    if (check instanceof PreCheck.RoleCheck roleCheck) {
      for (String name : roleCheck.anyOf()) {
        Role required = Role.valueOf(name.toUpperCase(Locale.ROOT));
        if (caller.satisfies(required)) {
          return null;
        }
      }
      return new CheckFailure("role", "caller role " + caller.name()
              + " satisfies none of " + roleCheck.anyOf());
    }
    if (check instanceof PreCheck.FeatureFlagCheck flagCheck) {
      if (flagSource.isEnabled(flagCheck.flag(), request)) {
        return null;
      }
      return new CheckFailure("feature-flag", "flag '" + flagCheck.flag() + "' is disabled");
    }
    if (check instanceof PreCheck.RateClassCheck rateCheck) {
      return evaluateRateClass(rateCheck, request);
    }
    log.warn("unknown preCheck type '{}' — denying", check.type());
    return new CheckFailure(check.type(), "unknown pre-check type '" + check.type() + "'");
  }

  private @Nullable CheckFailure evaluateRateClass(
          PreCheck.RateClassCheck check, HttpServletRequest request) {
    CbsDslManifestProperties.RateClass config = properties.rateClasses().get(check.rateClass());
    if (config == null) {
      log.warn("piece manifest names rate class '{}' with no matching "
              + "cbs.dsl.manifest.rate-classes.* config — failing closed", check.rateClass());
      return new CheckFailure("rate-class",
              "rate class '" + check.rateClass() + "' is not configured");
    }
    String key = check.rateClass() + "|" + principalKey(request);
    if (consume(key, config)) {
      return null;
    }
    return new CheckFailure("rate-class",
            "rate class '" + check.rateClass() + "' is exhausted for this principal");
  }

  private boolean consume(String key, CbsDslManifestProperties.RateClass config) {
    long now = nanoTime.getAsLong();
    Bucket bucket = buckets.compute(key, (k, current) -> {
      Bucket baseline = current == null ? new Bucket(config.capacity(), now, false) : current;
      long elapsedNanos = now - baseline.lastRefillNanos();
      double refill = elapsedNanos * config.refillPerSecond() / NANOS_PER_SECOND;
      double tokens = Math.min(config.capacity(), baseline.tokens() + refill);
      if (tokens >= 1.0) {
        return new Bucket(tokens - 1.0, now, true);
      }
      return new Bucket(tokens, now, false);
    });
    return bucket.consumed();
  }

  /**
   * Per-principal bucket identity: authenticated OIDC principal name first, then the API-key marker
   * ({@code ApiKeyAuthFilter} validates the key upstream; we only need identity granularity, not
   * the secret), then client IP as the anonymous fallback.
   */
  private static String principalKey(HttpServletRequest request) {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth != null && auth.isAuthenticated()
            && !(auth instanceof AnonymousAuthenticationToken)) {
      return "auth:" + auth.getName();
    }
    String apiKey = request.getHeader(StarterConstants.API_KEY_HEADER);
    if (apiKey != null && !apiKey.isBlank()) {
      return "apikey";
    }
    return "ip:" + request.getRemoteAddr();
  }

  private void onFailure(
          Piece piece,
          CheckFailure failure,
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    Role caller = roleResolver.resolve(request);
    if (FAIL_MODE_AUDIT_ONLY.equals(piece.failMode())) {
      log.warn("pre-check '{}' failed for piece '{}' (failMode=audit-only) — allowing: {}",
              failure.checkType(), piece.id(), failure.detail());
      audit(piece, failure, request);
      request.setAttribute(PIECE_ID_ATTRIBUTE, piece.id());
      request.setAttribute(PRINCIPAL_ROLE_ATTRIBUTE, caller.name());
      proceedAndTriggerPostChecks(piece, caller, request, response, filterChain);
      return;
    }
    log.warn("denying {} {} — pre-check '{}' failed for piece '{}': {}",
            request.getMethod(), request.getRequestURI(), failure.checkType(), piece.id(),
            failure.detail());
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(FORBIDDEN_CODE,
                    "Pre-check '" + failure.checkType() + "' failed for piece '" + piece.id()
                            + "': " + failure.detail(),
                    null, null, null, null, null, null,
                    Map.of("pieceId", piece.id(), "check", failure.checkType())));
  }

  private void audit(Piece piece, CheckFailure failure, HttpServletRequest request) {
    if (auditServiceProvider == null) {
      return;
    }
    DslAuditService auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), ACTION_PIECE_GUARD_DENY, piece.id(),
            correlationId(request), OUTCOME_FAILURE,
            Map.of("check", failure.checkType(), "failMode", piece.failMode(),
                    "route", request.getMethod() + " " + request.getRequestURI()));
  }

  private static @Nullable String correlationId(HttpServletRequest request) {
    try {
      return CorrelationId.validated(
              request.getHeader(StarterConstants.CORRELATION_ID_HEADER));
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  private record CheckFailure(String checkType, String detail) {
  }

  private record Bucket(double tokens, long lastRefillNanos, boolean consumed) {
  }
}
