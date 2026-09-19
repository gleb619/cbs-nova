package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.util.AntPathMatcher;
import org.yaml.snakeyaml.Yaml;

/**
 * Coverage guard for the piece-manifest (T553).
 *
 * <p>
 * Mirrors the {@link HelperDocsCoverageTest} idiom: discover the surfaced mutating routes from the
 * router source files, load the manifest YAML the same way, filter an {@code
 * ALLOWED_UNGUARDED} opt-out constant (one entry per item with a justification comment and a review
 * pointer), and assert the rest are covered. Skips gracefully outside the repo layout so the test
 * never hard-fails in a distributed build agent checkout.
 *
 * <h2>Reverse direction</h2> Catches orphan manifest entries: every {@code api} route named in the
 * manifest must correspond to an actual router source route. This is the same drift the FE uses for
 * BFF proxies.
 *
 * <h2>v1 discovery — source scan</h2> We deliberately source-scan the {@code config/router/}
 * directory for {@code .POST(}/{@code .PUT(}/{@code .DELETE(} invocations on the {@code
 * RouterFunctions.Builder}. Reflecting over {@code RouterFunction} trees is feasible but the
 * pattern in this codebase (all routers extend {@code AbstractRouterConfiguration} or hand-build a
 * {@code RouterFunctions.route()} lambda) is heterogeneous enough that a source scan is the
 * pragmatic v1. The scanner is intentionally narrow — adding a new mutating route that the test
 * fails to detect just means coverage is silently optimistic; the test still flags missing coverage
 * from the manifest side.
 */
class PieceManifestCoverageTest {

  private static final Path REPO_ROOT = Path.of("").toAbsolutePath()
          .normalize()
          .getParent().getParent().getParent();
  private static final Path ROUTER_SOURCE_DIR = REPO_ROOT
          .resolve("backend/dsl-starter/starter/src/main/java/cbs/nova/starter/config/router");
  private static final Path MANIFEST_YAML = REPO_ROOT
          .resolve("app/dsl/src/main/resources/piece-manifest.yaml");

  /**
   * Routes that intentionally have no manifest piece at this stage of T549 roll-out. Each entry
   * MUST carry a justification linking to the ticket expanding coverage. The list is expected to
   * shrink as T549 sweeps the rest of the surface.
   */
  private static final Set<String> ALLOWED_UNGUARDED = Set.of(
          // Draft authoring surface — guarded downstream by workbench-publish (T549 scope:
          // control-plane)
          "POST /api/dsl/drafts/{name}/save",
          // Draft publishing triggers dsl-reload via the workbench-publish manifest piece
          "POST /api/dsl/drafts/{name}/publish",
          // Draft history restore — operator-only audit, deferred to T620 audit-rewrite follow-up
          "POST /api/dsl/drafts/{name}/history/{timestamp}/restore",
          // Draft delete — degenerate from saving the empty draft, covered by workbench-publish
          // audit
          "DELETE /api/dsl/drafts/{name}",
          // Preview — sandbox-only execution, audit-only object policy will cover via T552
          // follow-up
          "POST /api/dsl/preview/{name}",
          // Hierarchy — static analysis, read-equivalent output; follow-up T621
          "POST /api/dsl/hierarchy/{name}",
          // Run — actual DSL execution entry; owner T551 (run-budget) added the manifest, this list
          // tracks remaining
          "POST /api/dsl/run/{name}",
          // Explain — static report, deferred to T621
          "POST /api/dsl/explain/{name}",
          // Manifest hot-reload is the manifest-piece administrative back door
          // (T549-self-bootstrap)
          "POST /api/dsl/manifest/reload",
          // Definition import — synonym for definitions bundle, covered by dsl-reload runtime piece
          "POST /api/dsl/definitions/import",
          // Environment promotion — control-plane bundle move between workbench directories,
          // recorded in dsl_audit (PROMOTION); T625 manifest piece pending
          "POST /api/dsl/promote",
          // Definition test suite replace — workbench-internal mutation; T622 will add the manifest
          // piece
          "PUT /api/dsl/definitions/{name}/tests",
          // Definition test run — feeds the preview pipeline; covered transitively by run manifest
          // piece (T551)
          "POST /api/dsl/definitions/{name}/tests/run",
          // Schedule create — Temporal schedule bridge; T623 manifest piece pending
          "POST /api/dsl/schedules",
          "POST /api/dsl/schedules/{definition}/pause",
          "POST /api/dsl/schedules/{definition}/resume",
          "DELETE /api/dsl/schedules/{definition}",
          // File staging — workbench-only, no production data path; T624 manifest piece pending
          "POST /api/dsl/files/by-name/{name}",
          "POST /api/dsl/files/bulk",
          "POST /api/dsl/files/flush",
          "POST /api/dsl/files/{*path}",
          // API key admin — separate admin path; T625 manifest piece pending
          "POST /api/dsl/auth/keys",
          "DELETE /api/dsl/auth/keys/{id}",
          // VHS load-test — operational tool, not a production data path; T560
          "POST /api/v1/vhs/loadtest",
          // Notification rule CRUD — control-plane rule config; T565 follow-up manifest piece
          // pending
          "POST /api/dsl/notifications/rules",
          "PUT /api/dsl/notifications/rules/{id}",
          "DELETE /api/dsl/notifications/rules/{id}",
          "POST /api/dsl/notifications/rules/{id}/enabled",
          // Notification rule synthetic test run — read-only match check, nothing persisted
          "POST /api/dsl/notifications/test",
          // Change-request submit/approve/reject — the T568 approval gate itself; the approve
          // path delegates to workbench-publish, manifest piece T626 pending
          "POST /api/dsl/drafts/{name}/change-request",
          "POST /api/dsl/change-requests/{id}/approve",
          "POST /api/dsl/change-requests/{id}/reject");

  private static final Pattern ROUTE_INVOCATION = Pattern.compile(
          "\\.(?:POST|PUT|DELETE)\\(\\s*\"([^\"]+)\"");
  private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

  @Test
  void everyMutatingRouterRouteIsCoveredByManifestPiece() throws IOException {
    assumeTrue(Files.exists(MANIFEST_YAML),
            () -> "piece manifest not found at " + MANIFEST_YAML
                    + " (running outside the repo layout); skipping manifest-coverage check");
    assumeTrue(Files.exists(ROUTER_SOURCE_DIR),
            () -> "router source dir not found at " + ROUTER_SOURCE_DIR
                    + " (running outside the repo layout); skipping manifest-coverage check");

    List<String> routerRoutes = discoverRouterRoutes();
    List<String> manifestRoutes = discoverManifestApiRoutes();

    assertNoOrphanManifestRoutes(manifestRoutes, routerRoutes);
    assertEveryRouterRouteCovered(routerRoutes, manifestRoutes);
  }

  @SuppressWarnings("unchecked")
  private static List<String> discoverManifestApiRoutes() throws IOException {
    Object root;
    try (var in = Files.newInputStream(MANIFEST_YAML)) {
      root = new Yaml().load(in);
    }
    if (!(root instanceof Map<?, ?> map) || !(map.get("pieces") instanceof List<?> pieces)) {
      return List.of();
    }
    List<String> routes = new ArrayList<>();
    for (Object item : pieces) {
      if (!(item instanceof Map<?, ?> piece)) {
        continue;
      }
      Object target = piece.get("target");
      if (!(target instanceof Map<?, ?> targetMap)) {
        continue;
      }
      if (!"api".equals(targetMap.get("type"))) {
        continue;
      }
      Object route = targetMap.get("route");
      if (route instanceof String s && !s.isBlank()) {
        routes.add(s.trim());
      }
    }
    return List.copyOf(routes);
  }

  private static List<String> discoverRouterRoutes() throws IOException {
    List<String> routes = new ArrayList<>();
    try (var stream = Files.list(ROUTER_SOURCE_DIR)) {
      List<Path> files = stream.filter(p -> p.getFileName().toString().endsWith(".java")).toList();
      for (Path file : files) {
        String source = Files.readString(file);
        Matcher matcher = ROUTE_INVOCATION.matcher(source);
        while (matcher.find()) {
          String path = matcher.group(1);
          int before = matcher.start();
          // Recover the method token from the matched invocation (.POST / .PUT / .DELETE).
          int dot = source.lastIndexOf('.', before);
          int paren = source.indexOf('(', dot);
          String method = source.substring(dot + 1, paren).toUpperCase(Locale.ROOT);
          if (Set.of("POST", "PUT", "DELETE").contains(method)) {
            routes.add(method + " " + path);
          }
        }
      }
    }
    // The fs walk order is not stable across platforms — sort so the diagnostic message is
    // deterministic.
    java.util.Collections.sort(routes);
    return List.copyOf(routes);
  }

  private static void assertNoOrphanManifestRoutes(List<String> manifestRoutes,
          List<String> routerRoutes) {
    List<Route> parsedRouters = routerRoutes.stream().map(Route::parse).toList();
    List<String> orphans = new ArrayList<>();
    for (String manifestRoute : manifestRoutes) {
      Route m = Route.parse(manifestRoute);
      boolean matched = parsedRouters.stream()
              .anyMatch(r -> r.method.equals(m.method) && PATH_MATCHER.match(m.path, r.path));
      if (!matched) {
        orphans.add(manifestRoute);
      }
    }
    assertThat(orphans)
            .as("Manifest 'api' route(s) reference a route no router defines"
                    + " — remove the stale piece or restore the missing router (manifest routes: %s)",
                    manifestRoutes)
            .isEmpty();
  }

  private static void assertEveryRouterRouteCovered(List<String> routerRoutes,
          List<String> manifestRoutes) {
    List<Route> manifest = manifestRoutes.stream().map(Route::parse).toList();
    List<String> missing = new ArrayList<>();
    Map<String, String> diagnoses = new LinkedHashMap<>();
    for (String routerRoute : routerRoutes) {
      if (ALLOWED_UNGUARDED.contains(routerRoute)) {
        continue;
      }
      Route r = Route.parse(routerRoute);
      boolean covered = manifest.stream()
              .anyMatch(m -> m.method.equals(r.method) && PATH_MATCHER.match(m.path, r.path));
      if (!covered) {
        missing.add(routerRoute);
        diagnoses.put(routerRoute, "no manifest piece matches " + r.method + " " + r.path
                + " — add a piece to " + MANIFEST_YAML
                + " or add to ALLOWED_UNGUARDED with justification");
      }
    }
    assertThat(missing)
            .as("Mutating router route(s) have no manifest piece and no ALLOWED_UNGUARDED entry"
                    + " (router routes: %s; diagnoses: %s)"
                    + " — add a piece to " + MANIFEST_YAML
                    + " or add the route to ALLOWED_UNGUARDED with a justification comment",
                    routerRoutes, diagnoses)
            .isEmpty();
  }

  private record Route(String method, String path) {

    static Route parse(String route) {
      int space = route.indexOf(' ');
      if (space < 0 || space == route.length() - 1) {
        throw new IllegalArgumentException("invalid route spec: " + route);
      }
      return new Route(route.substring(0, space).toUpperCase(Locale.ROOT),
              route.substring(space + 1));
    }
  }
}
