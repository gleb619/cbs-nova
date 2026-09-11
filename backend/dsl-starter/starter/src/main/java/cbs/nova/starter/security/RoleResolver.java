package cbs.nova.starter.security;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.web.ApiKeyAuthFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import lombok.RequiredArgsConstructor;

/**
 * Resolves the highest {@link Role} of the caller for a given {@link HttpServletRequest}.
 *
 * <p>
 * Resolution order (highest priority first):
 * <ol>
 * <li><b>API key</b>: any non-blank {@code X-Api-Key} header is treated as a service-to-service
 * caller → {@link Role#ADMIN}. The {@link ApiKeyAuthFilter} itself does not populate the Spring
 * Security context (it only short-circuits the chain on missing/invalid keys), so the filter is the
 * source of truth for "did the caller present a valid API key?" and the resolver mirrors that by
 * reading the header directly.</li>
 * <li><b>OIDC JWT</b>: when a {@link JwtAuthenticationToken} is present in the
 * {@link SecurityContextHolder}, the configured claim (supplied by
 * {@link cbs.nova.starter.config.RbacFilterConfiguration}; blank falls back to
 * {@link StarterConstants#DEFAULT_CLAIM_NAME}) is read. If the configured claim name is
 * {@code "roles"} we also try {@code "scope"} / {@code "scp"} (OAuth 2.0 standard, space-delimited)
 * and merge the two sources so either convention works out of the box. Values are matched
 * case-insensitively against the {@link Role} names.</li>
 * <li><b>No authentication</b>: anonymous or null → {@link Role#VIEWER}.</li>
 * </ol>
 *
 * <p>
 * When no recognised roles are present in the JWT claim (or the claim is absent), the caller is
 * treated as {@link Role#VIEWER}. This keeps read-only introspection working for any authenticated
 * principal without explicit role assignment while still requiring the principal to be
 * authenticated at the outer security boundary (or the request to be anonymous-but-permitted when
 * RBAC is off).
 */
@RequiredArgsConstructor
public class RoleResolver {

  /** OAuth 2.0 standard scope claim, space-delimited per RFC 6749 §3.3. */
  private static final String SCOPE_CLAIM = "scope";
  /** Alternative scope claim used by some IdPs (Azure AD, Auth0). */
  private static final String SCP_CLAIM = "scp";

  private final String claimName;

  /**
   * Resolve the caller's role for the given request.
   */
  public Role resolve(HttpServletRequest request) {
    if (isApiKeyPrincipal(request)) {
      return Role.ADMIN;
    }
    return resolveFromSecurityContext();
  }

  private static boolean isApiKeyPrincipal(HttpServletRequest request) {
    if (request == null) {
      return false;
    }
    String header = request.getHeader(StarterConstants.API_KEY_HEADER);
    return header != null && !header.isBlank();
  }

  private Role resolveFromSecurityContext() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null
            || !auth.isAuthenticated()
            || auth instanceof AnonymousAuthenticationToken) {
      return Role.VIEWER;
    }
    Set<String> values = collectRoleValues(auth);
    return highestRoleOrViewer(values);
  }

  private Set<String> collectRoleValues(Authentication auth) {
    Set<String> values = new LinkedHashSet<>();
    if (auth instanceof JwtAuthenticationToken jwtAuth) {
      Jwt jwt = jwtAuth.getToken();
      addClaim(values, jwt, claimName);
      // Only fall back to scope-style claims when the configured claim is the default "roles",
      // because a custom claim like "cbs_roles" is intentionally exclusive and shouldn't pick up
      // unrelated "scope" values from an OIDC provider.
      if (StarterConstants.DEFAULT_CLAIM_NAME.equals(claimName)) {
        addClaim(values, jwt, StarterConstants.OAUTH_SCOPE_CLAIM);
        addClaim(values, jwt, StarterConstants.OAUTH_SCP_CLAIM);
      }
    }
    // Also accept Spring Security GrantedAuthority strings so phase-2 mappers (e.g. a
    // JwtAuthenticationConverter that emits SCOPE_xxx authorities) don't require a code change.
    for (GrantedAuthority ga : auth.getAuthorities()) {
      if (ga != null && ga.getAuthority() != null) {
        values.add(stripAuthorityPrefix(ga.getAuthority()));
      }
    }
    return values;
  }

  private static void addClaim(Set<String> sink, Jwt jwt, String name) {
    Object raw = jwt.getClaim(name);
    if (raw == null) {
      return;
    }
    if (raw instanceof Collection<?> coll) {
      for (Object item : coll) {
        if (item != null) {
          sink.add(item.toString());
        }
      }
    } else if (raw instanceof String str) {
      // OAuth "scope" / "scp" claims are space-delimited per RFC 6749 §3.3.
      for (String token : str.split("\\s+")) {
        if (!token.isEmpty()) {
          sink.add(token);
        }
      }
    } else {
      sink.add(raw.toString());
    }
  }

  private static String stripAuthorityPrefix(String authority) {
    if (authority == null) {
      return null;
    }
    // Spring Security JwtGrantedAuthoritiesConverter prefixes scope claims with "SCOPE_".
    if (authority.startsWith("SCOPE_")) {
      return authority.substring("SCOPE_".length());
    }
    if (authority.startsWith("ROLE_")) {
      return authority.substring("ROLE_".length());
    }
    return authority;
  }

  private static Role highestRoleOrViewer(Set<String> values) {
    Role highest = Role.VIEWER;
    for (String value : values) {
      if (value == null) {
        continue;
      }
      String upper = value.toUpperCase(Locale.ROOT);
      for (Role candidate : Role.values()) {
        if (candidate.name().equals(upper) && candidate.rank() > highest.rank()) {
          highest = candidate;
        }
      }
    }
    return highest;
  }

  /**
   * Return the names of all {@link Role}s referenced by {@code values} (case-insensitive). Useful
   * for tests and diagnostics — not used by the filter hot path.
   */
  static List<String> matchedRoleNames(Set<String> values) {
    return values.stream()
            .filter(v -> v != null)
            .map(v -> v.toUpperCase(Locale.ROOT))
            .filter(upper -> {
              for (Role r : Role.values()) {
                if (r.name().equals(upper)) {
                  return true;
                }
              }
              return false;
            })
            .distinct()
            .toList();
  }
}
