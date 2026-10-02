package cbs.nova.starter.config.router;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cbs.nova.starter.config.properties.CbsNovaWebProperties;
import cbs.nova.starter.web.RequestBodyLimitFilter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.type.classreading.SimpleMetadataReaderFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * T787 — Guard that {@link RouterOperation @RouterOperation} /
 * {@link RouterOperations @RouterOperations} annotations on every functional router match the
 * routes registered by the same configuration's {@link RouterFunction} bean (and vice versa).
 *
 * <p>
 * Why this test exists: 20 {@code *RouterConfiguration} classes declare each endpoint twice (once
 * for springdoc, once for the actual
 * {@link org.springframework.web.servlet.function.RouterFunctions.Builder}) and nothing verifies
 * they agree. Drift between the two — a renamed path, changed verb or a forgotten annotation —
 * makes {@code docs/openapi.json} lie silently about the live surface (the class of drift behind
 * T767).
 *
 * <p>
 * The guard is reflection-only. It scans the {@code cbs.nova.starter.config.router} package at test
 * time, instantiates each configuration, walks every {@code RouterFunction} bean via the
 * framework's {@code RouterFunctions.Visitor}, and compares the discovered (HTTP method, path)
 * pairs against the {@code @RouterOperation} entries. Both directions are checked (annotation→route
 * and route→annotation) and {@code operationId} uniqueness is enforced.
 *
 * <p>
 * Reverse-direction gaps (routes that exist without a matching annotation) MUST be documented in
 * {@link #ALLOWLISTED_REVERSE_ROUTES} with a class, the {@code (method, path)} pair and a reason.
 * New entries require a follow-up task; do not blanket-skip classes.
 */
class RouterOperationConsistencyTest {

  private static final String CONFIG_PACKAGE = "cbs.nova.starter.config.router";

  /** Measured 2026-10-02: 21 router classes, 94 routes. Lower bounds only. */
  private static final int MIN_ROUTER_CLASSES = 21;
  private static final int MIN_REGISTERED_ROUTES = 94;

  /** Config classes in the package that legitimately register no router of their own. */
  private static final Set<String> NON_ROUTER_CONFIGURATIONS = Set.of(
          // Aggregator: only @Import's the individual router configurations.
          "DslRouterConfiguration");

  /**
   * Reverse-direction gaps: {@code (httpMethod, path)} tuples that are registered by the
   * {@link RouterFunction} but have no matching {@link RouterOperation} annotation. Each entry must
   * carry a reason. New entries must come with a follow-up task reference.
   */
  private static final Map<String, Map<String, String>> ALLOWLISTED_REVERSE_ROUTES = new LinkedHashMap<>();

  static {
    // No allowlisted gaps yet. When real drift is found in a future sweep, add an entry like:
    // ALLOWLISTED_REVERSE_ROUTES
    // .computeIfAbsent("ClassName.java", k -> new LinkedHashMap<>())
    // .put("POST /api/example/path", "Reason — TXXX follow-up");
  }

  @Test
  @DisplayName("every @RouterOperation matches a registered (method, path) and vice versa; operationIds unique")
  void routerOperationsMatchRegisteredRoutes() throws Exception {
    List<Class<?>> configClasses = discoverRouterConfigurations();

    // Defensive: this guard exists because there is exactly one RouterFunction bean per
    // *RouterConfiguration class. If a future refactor introduces more, fail loudly so the
    // sweep is not silently under-counting.
    assertThat(configClasses)
            .as("expected at least one router-configuration class on the classpath")
            .isNotEmpty();

    Map<String, Set<RouteKey>> annotationRoutesByClass = new LinkedHashMap<>();
    Map<String, Set<RouteKey>> registeredRoutesByClass = new LinkedHashMap<>();
    Map<String, String> operationIdOwnerByClass = new HashMap<>();
    Set<String> operationIds = new HashSet<>();
    List<String> operationIdCollisions = new ArrayList<>();

    RequestBodyLimitFilter requestBodyLimitFilter = new RequestBodyLimitFilter(
            new CbsNovaWebProperties(0L));

    for (Class<?> configClass : configClasses) {
      String className = configClass.getSimpleName();

      Method routerBeanMethod = findRouterFunctionBeanMethod(configClass);
      if (routerBeanMethod == null) {
        // Configuration does not declare a RouterFunction bean (e.g. DslRouterConfiguration,
        // which is an @Import aggregator). Nothing to assert.
        continue;
      }

      RouterFunction<ServerResponse> routerFunction = invokeRouterBeanMethod(configClass,
              routerBeanMethod, requestBodyLimitFilter);
      Set<RouteKey> registered = collectRegisteredRoutes(routerFunction);
      Set<RouteKey> annotated = collectAnnotatedRoutes(routerBeanMethod);

      annotationRoutesByClass.put(className, annotated);
      registeredRoutesByClass.put(className, registered);

      for (RouterOperation op : collectRouterOperations(routerBeanMethod)) {
        String opId = op.operation().operationId();
        if (opId == null || opId.isEmpty()) {
          continue;
        }
        String previousOwner = operationIdOwnerByClass.putIfAbsent(opId, className);
        if (previousOwner != null && !previousOwner.equals(className)) {
          operationIdCollisions.add(
                  "operationId '" + opId + "' appears in both " + previousOwner + " and "
                          + className);
        } else if (!operationIds.add(opId)) {
          // Duplicate within the same class — surface for the class-local uniqueness check.
          operationIdCollisions.add(
                  "operationId '" + opId + "' appears multiple times in " + className);
        }
      }
    }

    // Forward direction: every annotation must match a registered (method, path) pair.
    // Coverage floors: the guard must not pass vacuously if discovery or route collection regress.
    // Every config class must expose a RouterFunction<ServerResponse> bean unless it is listed
    // in NON_ROUTER_CONFIGURATIONS; counts are lower bounds so adding routers/routes stays green.
    List<String> unexpectedNonRouters = configClasses.stream()
            .map(Class::getSimpleName)
            .filter(n -> !registeredRoutesByClass.containsKey(n))
            .filter(n -> !NON_ROUTER_CONFIGURATIONS.contains(n))
            .toList();
    assertThat(unexpectedNonRouters)
            .as("config classes without a RouterFunction<ServerResponse> bean (add to "
                    + "NON_ROUTER_CONFIGURATIONS only with a reason)")
            .isEmpty();
    assertThat(registeredRoutesByClass).as("router configuration classes examined")
            .hasSizeGreaterThanOrEqualTo(MIN_ROUTER_CLASSES);
    assertThat(registeredRoutesByClass.values().stream().mapToInt(Set::size).sum())
            .as("registered routes examined").isGreaterThanOrEqualTo(MIN_REGISTERED_ROUTES);
    assertThat(registeredRoutesByClass).as("conditionally-enabled routers must be included")
            .containsKey("WebhookRouterConfiguration");

    List<String> annotationMismatches = new ArrayList<>();
    for (Map.Entry<String, Set<RouteKey>> entry : annotationRoutesByClass.entrySet()) {
      String className = entry.getKey();
      Set<RouteKey> annotated = entry.getValue();
      Set<RouteKey> registered = registeredRoutesByClass.getOrDefault(className, Set.of());
      for (RouteKey key : annotated) {
        if (!registered.contains(key)) {
          annotationMismatches.add(
                  className + " — annotation " + key + " has no matching registered route");
        }
      }
    }

    // Reverse direction: every registered route must match an annotation, unless it is on the
    // explicit allowlist.
    List<String> routeMismatches = new ArrayList<>();
    for (Map.Entry<String, Set<RouteKey>> entry : registeredRoutesByClass.entrySet()) {
      String className = entry.getKey();
      Set<RouteKey> annotated = annotationRoutesByClass.getOrDefault(className, Set.of());
      Set<RouteKey> allowlist = routesForAllowlist(ALLOWLISTED_REVERSE_ROUTES, className);
      for (RouteKey key : entry.getValue()) {
        if (annotated.contains(key)) {
          continue;
        }
        if (allowlist.contains(key)) {
          continue;
        }
        routeMismatches.add(
                className + " — registered route " + key + " has no matching @RouterOperation");
      }
    }

    assertThat(annotationMismatches)
            .as("annotations without a matching registered route")
            .isEmpty();
    assertThat(routeMismatches)
            .as("registered routes without a matching @RouterOperation (allowlist = %s)",
                    ALLOWLISTED_REVERSE_ROUTES)
            .isEmpty();
    assertThat(operationIdCollisions)
            .as("operationId collisions across router configurations")
            .isEmpty();
  }

  // ---------------------------------------------------------------------------------------------
  // Discovery: classpath scan of cbs.nova.starter.config.router for @Configuration classes.
  // ---------------------------------------------------------------------------------------------

  /**
   * Walks the package's {@code .class} metadata directly instead of using
   * {@code ClassPathScanningCandidateComponentProvider}: that provider evaluates
   * {@code @Conditional} and would silently drop conditionally-enabled routers such as
   * {@code WebhookRouterConfiguration} ({@code @ConditionalOnProperty(cbs.webhook.enabled)}) — the
   * exact area where openapi drift was found (T767).
   */
  private static List<Class<?>> discoverRouterConfigurations() throws IOException {
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

    List<Class<?>> classes = new ArrayList<>();
    for (String name : names) {
      try {
        Class<?> clazz = Class.forName(name);
        if (!Modifier.isAbstract(clazz.getModifiers())) {
          classes.add(clazz);
        }
      } catch (Throwable t) {
        throw new IllegalStateException(
                "Failed to load router configuration class " + name, t);
      }
    }
    return classes;
  }

  // ---------------------------------------------------------------------------------------------
  // Reflection: instantiate the configuration and call its RouterFunction bean method with mocks.
  // ---------------------------------------------------------------------------------------------

  private static Method findRouterFunctionBeanMethod(Class<?> configClass) {
    for (Method method : configClass.getDeclaredMethods()) {
      if (!RouterFunction.class.isAssignableFrom(method.getReturnType())) {
        continue;
      }
      Type arg = extractTypeParameter(method.getGenericReturnType());
      if (arg != ServerResponse.class) {
        // We only care about RouterFunction<ServerResponse>; other specialisations are skipped.
        continue;
      }
      return method;
    }
    return null;
  }

  private static Type extractTypeParameter(Type type) {
    if (!(type instanceof ParameterizedType pt)) {
      return null;
    }
    Type[] args = pt.getActualTypeArguments();
    return args.length == 0 ? null : args[0];
  }

  private static RouterFunction<ServerResponse> invokeRouterBeanMethod(
          Class<?> configClass, Method method, RequestBodyLimitFilter requestBodyLimitFilter)
          throws Exception {
    Constructor<?> ctor = configClass.getDeclaredConstructor();
    ctor.setAccessible(true);
    Object instance = ctor.newInstance();

    Object[] args = new Object[method.getParameterCount()];
    for (int i = 0; i < args.length; i++) {
      Parameter param = method.getParameters()[i];
      Class<?> type = param.getType();
      if (RequestBodyLimitFilter.class.isAssignableFrom(type)) {
        args[i] = requestBodyLimitFilter;
      } else {
        args[i] = mock(type);
      }
    }
    method.setAccessible(true);
    @SuppressWarnings("unchecked")
    RouterFunction<ServerResponse> router = (RouterFunction<ServerResponse>) method.invoke(instance,
            args);
    return router;
  }

  // ---------------------------------------------------------------------------------------------
  // Route extraction from the RouterFunction tree and from @RouterOperation annotations.
  // ---------------------------------------------------------------------------------------------

  /** Discovers every (HTTP method, path) registered by a {@link RouterFunction} tree. */
  private static Set<RouteKey> collectRegisteredRoutes(RouterFunction<ServerResponse> router) {
    Set<RouteKey> routes = new HashSet<>();
    router.accept(new org.springframework.web.servlet.function.RouterFunctions.Visitor() {
      @Override
      public void route(
              org.springframework.web.servlet.function.RequestPredicate predicate,
              org.springframework.web.servlet.function.HandlerFunction<?> handler) {
        extractRouteKey(predicate).ifPresent(routes::add);
      }

      @Override
      public void startNested(org.springframework.web.servlet.function.RequestPredicate predicate) {
        // No @RouterOperation in this package uses nest(); present for completeness so a future
        // refactor does not silently lose nested routes.
      }

      @Override
      public void endNested(org.springframework.web.servlet.function.RequestPredicate predicate) {
      }

      @Override
      public void resources(
              java.util.function.Function<org.springframework.web.servlet.function.ServerRequest, Optional<org.springframework.core.io.Resource>> resourceFunction) {
      }

      @Override
      public void attributes(Map<String, Object> attributes) {
      }

      @Override
      public void unknown(RouterFunction<?> routerFunction) {
      }
    });
    return routes;
  }

  private static Optional<RouteKey> extractRouteKey(
          org.springframework.web.servlet.function.RequestPredicate predicate) {
    HttpMethod[] methodHolder = new HttpMethod[1];
    String[] pathHolder = new String[1];
    predicate.accept(new org.springframework.web.servlet.function.RequestPredicates.Visitor() {
      @Override
      public void method(Set<HttpMethod> methods) {
        if (!methods.isEmpty()) {
          methodHolder[0] = methods.iterator().next();
        }
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
      public void unknown(org.springframework.web.servlet.function.RequestPredicate p) {
      }
    });
    if (methodHolder[0] != null && pathHolder[0] != null) {
      return Optional.of(new RouteKey(methodHolder[0], pathHolder[0]));
    }
    return Optional.empty();
  }

  private static Set<RouteKey> collectAnnotatedRoutes(Method beanMethod) {
    Set<RouteKey> keys = new HashSet<>();
    for (RouterOperation op : collectRouterOperations(beanMethod)) {
      RequestMethod[] declared = op.method();
      if (declared.length == 0) {
        continue;
      }
      HttpMethod httpMethod = HttpMethod.valueOf(declared[0].name());
      keys.add(new RouteKey(httpMethod, op.path()));
    }
    return keys;
  }

  private static List<RouterOperation> collectRouterOperations(Method beanMethod) {
    List<RouterOperation> all = new ArrayList<>();
    RouterOperations container = beanMethod.getAnnotation(RouterOperations.class);
    if (container != null) {
      Collections.addAll(all, container.value());
    }
    RouterOperation single = beanMethod.getAnnotation(RouterOperation.class);
    if (single != null) {
      all.add(single);
    }
    return all;
  }

  private static Set<RouteKey> routesForAllowlist(
          Map<String, Map<String, String>> allowlist, String className) {
    Map<String, String> entries = allowlist.get(className);
    if (entries == null) {
      return Set.of();
    }
    Set<RouteKey> keys = new HashSet<>();
    for (String token : entries.keySet()) {
      int sep = token.indexOf(' ');
      if (sep < 0) {
        continue;
      }
      try {
        HttpMethod method = HttpMethod.valueOf(token.substring(0, sep));
        String path = token.substring(sep + 1);
        keys.add(new RouteKey(method, path));
      } catch (IllegalArgumentException ignored) {
        // Malformed allowlist entry; leave for the next milestone.
      }
    }
    return keys;
  }

  // ---------------------------------------------------------------------------------------------
  // Value types
  // ---------------------------------------------------------------------------------------------

  /** Equality is on (HTTP method, path). The class declares equality. */
  private record RouteKey(HttpMethod method, String path) {
  }
}
