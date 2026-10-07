package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * T923 — Drift guard for the OIDC protected-path set.
 *
 * <p>
 * When {@code cbs.security.oidc.enabled=true}, every registered {@code /api/**} route must be
 * covered by either {@code cbs.security.oidc.protected-paths} (requires Bearer JWT) or
 * {@code cbs.security.oidc.permit-all-paths} (intentionally public). The default protected set is
 * {@code ["/api/**"]}, so in practice only explicitly public routes should appear in the permit-all
 * allowlist.
 *
 * <p>
 * This test scans the router-configuration package directly from bytecode metadata (the same
 * technique as {@link cbs.nova.starter.config.router.RouterOperationConsistencyTest}) so it sees
 * conditionally-enabled routers such as {@code WebhookRouterConfiguration} and
 * {@code VhsManagementRouterConfiguration} even when they are not active in the current test
 * context. Annotation-mapped {@code @RestController} routes (the SSE endpoints) are not part of
 * this static scan; they are covered by the runtime audit
 * ({@link cbs.nova.starter.config.OidcProtectedPathAudit}) asserted in
 * {@link SecurityConfigurationTest}.
 */
class OidcProtectedPathDriftTest {

  private static final String CONFIG_PACKAGE = "cbs.nova.starter.config.router";

  /**
   * Intentionally public {@code /api/**} routes. Each entry must include a reason explaining why
   * OIDC authentication is not required (e.g. inbound webhook triggers that authenticate via
   * HMAC/signature, a public auth callback, or a CSP report sink).
   */
  private static final Map<String, String> PUBLIC_API_PATH_ALLOWLIST = new LinkedHashMap<>();

  static {
    // No public /api/** routes today. Add entries only with a security-reviewed reason.
  }

  @Test
  @DisplayName("every registered /api/** route is covered by OIDC protected-paths or permit-all-paths")
  void everyApiRouteIsProtectedOrExplicitlyPublic() throws Exception {
    CbsSecurityOidcProperties defaults = new CbsSecurityOidcProperties(false, null, null);

    List<PathPattern> protectedPatterns = parsePatterns(defaults.protectedPaths());
    List<PathPattern> permitAllPatterns = parsePatterns(defaults.permitAllPaths());

    List<String> uncovered = new ArrayList<>();
    Set<String> coveredByPermitAll = new LinkedHashSet<>();
    int apiRouteCount = 0;

    for (RouterOperation op : discoverRouterOperations()) {
      if (op.method().length == 0) {
        continue;
      }
      String path = op.path();
      if (path == null || !path.startsWith("/api/")) {
        continue;
      }
      apiRouteCount++;
      String method = op.method()[0].name();
      String spec = method + " " + path;
      String allowReason = PUBLIC_API_PATH_ALLOWLIST.get(spec);
      if (matchesAny(path, protectedPatterns)) {
        continue;
      }
      if (allowReason != null && matchesAny(path, permitAllPatterns)) {
        coveredByPermitAll.add(spec + " -> " + allowReason);
        continue;
      }
      if (matchesAny(path, permitAllPatterns)) {
        coveredByPermitAll.add(spec);
        continue;
      }
      uncovered.add(spec);
    }

    // Discovery floor: a silent scan regression must not turn this guard into a no-op.
    assertThat(apiRouteCount)
            .as("classpath scan must discover the registered /api router surface")
            .isGreaterThanOrEqualTo(90);

    List<String> staleAllowlist = PUBLIC_API_PATH_ALLOWLIST.keySet().stream()
            .filter(key -> coveredByPermitAll.stream()
                    .noneMatch(entry -> entry.startsWith(key + " -> ")))
            .toList();
    assertThat(staleAllowlist)
            .as("allowlist entries must match a registered permit-all /api route")
            .isEmpty();

    assertThat(coveredByPermitAll)
            .as("permit-all /api/** routes must be documented in PUBLIC_API_PATH_ALLOWLIST")
            .isEmpty();

    assertThat(uncovered)
            .as("registered /api/** routes not covered by default protected-paths or permit-all-paths"
                    + " — add the route to protected-paths, permit-all-paths, or the allowlist")
            .isEmpty();
  }

  private static List<PathPattern> parsePatterns(List<String> patterns) {
    PathPatternParser parser = new PathPatternParser();
    return patterns.stream().map(parser::parse).toList();
  }

  private static boolean matchesAny(String path, List<PathPattern> patterns) {
    for (PathPattern pattern : patterns) {
      if (pattern.matches(PathContainer.parsePath(path))) {
        return true;
      }
    }
    return false;
  }

  private static List<RouterOperation> discoverRouterOperations() throws IOException {
    List<RouterOperation> result = new ArrayList<>();
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    SimpleMetadataReaderFactory readers = new SimpleMetadataReaderFactory();
    TreeSet<String> names = new TreeSet<>();
    for (Resource resource : resolver.getResources(
            "classpath*:" + CONFIG_PACKAGE.replace('.', '/') + "/*.class")) {
      var metadata = readers.getMetadataReader(resource).getAnnotationMetadata();
      if (metadata.isAnnotated(Configuration.class.getName())) {
        names.add(metadata.getClassName());
      }
    }
    for (String name : names) {
      Class<?> clazz;
      try {
        clazz = Class.forName(name);
      } catch (ClassNotFoundException e) {
        throw new IllegalStateException("Failed to load router configuration class " + name, e);
      }
      for (Method method : clazz.getDeclaredMethods()) {
        RouterOperations container = method.getAnnotation(RouterOperations.class);
        if (container != null) {
          Collections.addAll(result, container.value());
        }
        RouterOperation single = method.getAnnotation(RouterOperation.class);
        if (single != null) {
          result.add(single);
        }
      }
    }
    return result;
  }
}
