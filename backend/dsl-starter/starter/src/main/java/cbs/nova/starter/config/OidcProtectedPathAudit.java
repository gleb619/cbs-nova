package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsSecurityOidcProperties;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.PathContainer;
import org.springframework.web.servlet.function.HandlerFunction;
import org.springframework.web.servlet.function.RequestPredicate;
import org.springframework.web.servlet.function.RequestPredicates;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * T923 — startup audit for the OIDC protected-path set.
 *
 * <p>
 * When {@code cbs.security.oidc.enabled=true}, this component enumerates every route actually
 * registered in the application context (functional {@link RouterFunction} beans plus annotation
 * {@code @RequestMapping} controllers such as the SSE endpoints) and logs a WARN for each
 * registered {@code /api/**} route that is covered by neither
 * {@code cbs.security.oidc.protected-paths} nor {@code cbs.security.oidc.permit-all-paths} — i.e. a
 * route the enabled OIDC chain would serve to anonymous callers.
 *
 * <p>
 * The audit is diagnostic only: it never fails startup (enumeration errors are logged as WARN) and
 * never changes the filter chain. It complements the static {@code OidcProtectedPathDriftTest}
 * (classpath scan of the router-configuration package) with runtime truth from the live context,
 * which also covers conditionally-registered routers and annotation controllers.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(name = "cbs.security.oidc.enabled", havingValue = "true")
@EnableConfigurationProperties(CbsSecurityOidcProperties.class)
public class OidcProtectedPathAudit implements SmartInitializingSingleton {

  private final ApplicationContext context;
  private final CbsSecurityOidcProperties properties;

  public OidcProtectedPathAudit(ApplicationContext context,
          CbsSecurityOidcProperties properties) {
    this.context = context;
    this.properties = properties;
  }

  @Override
  public void afterSingletonsInstantiated() {
    List<String> uncovered;
    try {
      uncovered = findUnprotectedApiRoutes();
    } catch (RuntimeException ex) {
      log.warn("OIDC protected-path audit failed to enumerate registered routes — cannot verify "
              + "that every /api/** route requires a Bearer JWT", ex);
      return;
    }
    if (!uncovered.isEmpty()) {
      log.warn("cbs.security.oidc.enabled=true but {} registered /api/** route(s) require no "
              + "Bearer JWT (outside cbs.security.oidc.protected-paths={} and "
              + "cbs.security.oidc.permit-all-paths={}): {}. Anonymous callers can reach them. "
              + "Add them to protected-paths, or to permit-all-paths with a documented reason, "
              + "before exposing this instance.",
              uncovered.size(), properties.protectedPaths(), properties.permitAllPaths(),
              uncovered);
    }
  }

  /** Registered {@code /api/**} routes that the enabled OIDC chain leaves anonymous. */
  List<String> findUnprotectedApiRoutes() {
    return uncoveredApiRoutes(collectRegisteredApiRoutes(), properties);
  }

  /**
   * Every registered {@code /api/**} route pattern in the live context: functional
   * {@link RouterFunction} beans (all {@code config/router} endpoints) plus annotation-mapped
   * controllers (SSE streams).
   */
  Set<String> collectRegisteredApiRoutes() {
    Set<String> routes = new TreeSet<>();
    for (RouterFunction<?> router : context.getBeansOfType(RouterFunction.class).values()) {
      collectRouterRoutes(router, routes);
    }
    for (RequestMappingHandlerMapping mapping : context
            .getBeansOfType(RequestMappingHandlerMapping.class).values()) {
      mapping.getHandlerMethods().forEach((info, handlerMethod) -> {
        Set<String> patterns = info.getPatternValues();
        if (patterns == null) {
          return;
        }
        patterns.forEach(pattern -> {
          if (isApiRoute(pattern)) {
            routes.add(pattern);
          }
        });
      });
    }
    return routes;
  }

  static List<String> uncoveredApiRoutes(Collection<String> registeredRoutes,
          CbsSecurityOidcProperties properties) {
    List<PathPattern> protectedPatterns = parsePatterns(properties.protectedPaths());
    List<PathPattern> permitAllPatterns = parsePatterns(properties.permitAllPaths());
    List<String> uncovered = new ArrayList<>();
    for (String route : registeredRoutes) {
      if (!isApiRoute(route)) {
        continue;
      }
      if (matchesAny(route, protectedPatterns) || matchesAny(route, permitAllPatterns)) {
        continue;
      }
      uncovered.add(route);
    }
    return uncovered;
  }

  private static void collectRouterRoutes(RouterFunction<?> router, Set<String> routes) {
    router.accept(new RouterFunctions.Visitor() {
      @Override
      public void route(RequestPredicate predicate, HandlerFunction<?> handler) {
        extractPath(predicate).ifPresent(path -> {
          if (isApiRoute(path)) {
            routes.add(path);
          }
        });
      }

      @Override
      public void startNested(RequestPredicate predicate) {
      }

      @Override
      public void endNested(RequestPredicate predicate) {
      }

      @Override
      public void resources(Function<ServerRequest, Optional<Resource>> resourceFunction) {
      }

      @Override
      public void attributes(Map<String, Object> attributes) {
      }

      @Override
      public void unknown(RouterFunction<?> function) {
      }
    });
  }

  private static Optional<String> extractPath(RequestPredicate predicate) {
    String[] pathHolder = new String[1];
    predicate.accept(new RequestPredicates.Visitor() {
      @Override
      public void method(Set<HttpMethod> methods) {
      }

      @Override
      public void path(String path) {
        pathHolder[0] = path;
      }

      @Override
      public void startAnd() {
      }

      @Override
      public void and() {
      }

      @Override
      public void endAnd() {
      }

      @Override
      public void startOr() {
      }

      @Override
      public void or() {
      }

      @Override
      public void endOr() {
      }

      @Override
      public void startNegate() {
      }

      @Override
      public void endNegate() {
      }

      @Override
      public void pathExtension(String extension) {
      }

      @Override
      public void header(String name, String value) {
      }

      @Override
      public void param(String name, String value) {
      }

      @Override
      public void version(String version) {
      }

      @Override
      public void unknown(RequestPredicate p) {
      }
    });
    return Optional.ofNullable(pathHolder[0]);
  }

  private static List<PathPattern> parsePatterns(List<String> patterns) {
    PathPatternParser parser = new PathPatternParser();
    List<PathPattern> parsed = new ArrayList<>();
    if (patterns == null) {
      return parsed;
    }
    for (String pattern : patterns) {
      parsed.add(parser.parse(pattern));
    }
    return parsed;
  }

  private static boolean matchesAny(String route, List<PathPattern> patterns) {
    PathContainer container = PathContainer.parsePath(route);
    for (PathPattern pattern : patterns) {
      if (pattern.matches(container)) {
        return true;
      }
    }
    return false;
  }

  private static boolean isApiRoute(String route) {
    return route != null && (route.equals("/api") || route.startsWith("/api/"));
  }
}
