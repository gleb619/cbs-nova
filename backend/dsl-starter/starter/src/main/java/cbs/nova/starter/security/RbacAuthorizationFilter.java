package cbs.nova.starter.security;

import cbs.nova.starter.config.RbacFilterConfiguration;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.dsl.model.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

/**
 * Servlet filter that enforces the (route, method) → {@link Role} table for every {@code /api/**}
 * request. Registered AFTER {@link cbs.nova.starter.web.ApiKeyAuthFilter} so an authenticated
 * principal is available when {@link RoleResolver} runs.
 *
 * <p>
 * The filter is OPT-IN: it is registered only when {@code cbs.dsl.auth.rbac.enabled=true} (see
 * {@link RbacFilterConfiguration}). When absent from the context every request is unaffected — the
 * default-mode behaviour matches the pre-T408 starter.
 *
 * <p>
 * On every {@code /api/**} request the filter:
 * <ol>
 * <li>Computes the {@link Role} required for {@code (path, method)} using
 * {@link #requiredRole}.</li>
 * <li>Resolves the caller's role via {@link RoleResolver}.</li>
 * <li>If the caller's role satisfies the required role, the request is forwarded to the chain.</li>
 * <li>Otherwise the filter writes a 403 response with an {@link ErrorResponse} body
 * ({@code code = "FORBIDDEN"}, message naming the required role and the caller's principal name)
 * and stops the chain.</li>
 * </ol>
 *
 * <p>
 * Fail-closed: when an {@code /api/**} mutating route does not match any rule the filter requires
 * {@link Role#OPERATOR} and emits a WARN log so the missing rule can be diagnosed. Reads always
 * fall back to {@link Role#VIEWER}.
 */
@Slf4j
public final class RbacAuthorizationFilter extends OncePerRequestFilter {

  private static final List<RouteRule> RULES = List.of(
          // --- RUNNER: run / preview / explain / cancel ---
          new RouteRule(HttpMethod.POST, "/api/dsl/preview/**", Role.RUNNER),
          new RouteRule(HttpMethod.POST, "/api/dsl/run/**", Role.RUNNER),
          new RouteRule(HttpMethod.POST, "/api/dsl/explain/**", Role.RUNNER),
          new RouteRule(HttpMethod.POST, "/api/executions/*/cancel", Role.RUNNER),
          // --- AUTHOR: draft writes / publish / reload / file staging / bundle
          // import ---
          new RouteRule(HttpMethod.POST, "/api/dsl/reload", Role.AUTHOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/drafts/*/save", Role.AUTHOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/drafts/*/publish", Role.AUTHOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/drafts/*/history/*/restore", Role.AUTHOR),
          new RouteRule(HttpMethod.DELETE, "/api/dsl/drafts/*", Role.AUTHOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/files/**", Role.AUTHOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/definitions/import", Role.AUTHOR),
          // --- OPERATOR: schedule CRUD ---
          new RouteRule(HttpMethod.POST, "/api/dsl/schedules", Role.OPERATOR),
          new RouteRule(HttpMethod.DELETE, "/api/dsl/schedules/*", Role.OPERATOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/schedules/*/pause", Role.OPERATOR),
          new RouteRule(HttpMethod.POST, "/api/dsl/schedules/*/resume", Role.OPERATOR),
          // --- OPERATOR: rotatable API-key admin surface (T410) ---
          new RouteRule(HttpMethod.POST, "/api/dsl/auth/keys", Role.OPERATOR),
          new RouteRule(HttpMethod.DELETE, "/api/dsl/auth/keys/*", Role.OPERATOR));

  private final RoleResolver roleResolver;
  private final ObjectMapper objectMapper;
  private final AntPathMatcher pathMatcher = new AntPathMatcher();

  public RbacAuthorizationFilter(RoleResolver roleResolver, ObjectMapper objectMapper) {
    this.roleResolver = roleResolver;
    this.objectMapper = objectMapper;
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws ServletException, IOException {
    Role required = requiredRole(request);
    Role caller = roleResolver.resolve(request);
    if (caller.satisfies(required)) {
      filterChain.doFilter(request, response);
      return;
    }
    String principal = caller.name();
    log.warn("denying {} {} — caller role {} < required role {} (principal={})",
            request.getMethod(), request.getRequestURI(), caller, required, principal);
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(),
            new ErrorResponse(StarterConstants.FORBIDDEN_CODE, "Role " + required.name()
                    + " is required for " + request.getMethod()
                    + " " + request.getRequestURI()
                    + "; caller has role " + principal, null, null, null, null, null, null, null));
  }

  /**
   * Compute the role required for {@code request}. Reads always default to {@link Role#VIEWER};
   * unmatched mutating requests default to {@link Role#OPERATOR} (the strictest sensible default —
   * never fail open) and emit a WARN so the rule gap is visible in logs.
   */
  Role requiredRole(HttpServletRequest request) {
    String method = request.getMethod() == null
            ? ""
            : request.getMethod().toUpperCase(Locale.ROOT);
    String path = request.getRequestURI();
    for (RouteRule rule : RULES) {
      if (rule.matches(method, path, pathMatcher)) {
        return rule.required();
      }
    }
    if (HttpMethod.GET.matches(method) || HttpMethod.HEAD.matches(method)
            || HttpMethod.OPTIONS.matches(method)) {
      return Role.VIEWER;
    }
    log.warn("no RBAC rule for {} {} — defaulting to OPERATOR (strict) — add a rule explicitly",
            method, path);
    return Role.OPERATOR;
  }

  private record RouteRule(HttpMethod method, String pattern, Role required) {

    boolean matches(String methodUpper, String path, AntPathMatcher matcher) {
      return method.name().equals(methodUpper) && matcher.match(pattern, path);
    }
  }
}
