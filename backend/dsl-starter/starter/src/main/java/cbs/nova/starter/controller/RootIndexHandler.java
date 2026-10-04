package cbs.nova.starter.controller;

import cbs.nova.starter.config.properties.CbsNovaIndexProperties;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Optional root index handler. Injects the static HTML resource and serves it for browser-like
 * requests; returns JSON metadata for everything else.
 */
@RequiredArgsConstructor
public class RootIndexHandler implements ApplicationListener<ApplicationReadyEvent> {

  private static final List<String> DSL_ROUTES = List.of(
          "POST /api/dsl/preview/{name}",
          "POST /api/dsl/hierarchy/{name}",
          "POST /api/dsl/run/{name}",
          "POST /api/dsl/explain/{name}");

  private final CbsNovaIndexProperties properties;
  private final Environment environment;
  private final RequestMappingHandlerMapping handlerMapping;
  private final Resource indexHtml;
  private final AtomicReference<Instant> readyTime = new AtomicReference<>();

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    readyTime.set(Instant.now());
  }

  public ServerResponse index(ServerRequest request) {
    if (!properties.enabled()) {
      return ServerResponse.notFound().build();
    }

    if (wantsHtml(request)) {
      return ServerResponse.ok()
              .contentType(MediaType.TEXT_HTML)
              .body(indexHtml);
    }
    return ServerResponse.ok().body(buildInfo());
  }

  private boolean wantsHtml(ServerRequest request) {
    List<MediaType> accepted = request.headers().accept();
    if (accepted.isEmpty()) {
      return true;
    }
    return accepted.stream().anyMatch(MediaType.TEXT_HTML::isCompatibleWith);
  }

  private AppInfo buildInfo() {
    Instant start = readyTime.get();
    long uptimeSeconds = start == null ? 0 : Duration.between(start, Instant.now()).getSeconds();

    String appName = environment.getProperty("spring.application.name", "cbs-nova");
    String port = environment.getProperty("server.port", "8080");

    List<String> routes = new ArrayList<>(DSL_ROUTES);
    handlerMapping.getHandlerMethods().forEach((info, method) -> {
      if (info.getPathPatternsCondition() != null) {
        info.getPathPatternsCondition().getPatterns()
                .forEach(p -> routes.add(p.getPatternString()));
      }
    });
    routes.add("GET /");
    routes.sort(Comparator.naturalOrder());

    return new AppInfo(appName, port, uptimeSeconds, "UP",
            start == null ? Instant.now().toEpochMilli() : start.toEpochMilli(), routes);
  }

  private record AppInfo(String name, String port, long uptimeSeconds, String health,
          long startedAt, List<String> routes) {
  }
}
