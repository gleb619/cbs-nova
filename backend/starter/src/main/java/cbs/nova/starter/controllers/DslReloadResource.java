package cbs.nova.starter.controllers;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.models.ErrorResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Functional handler for the DSL reload endpoint. Registered as a {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.DslReloadRouterConfiguration} (gated by
 * {@code dsl.reload.enabled}, on by default) rather than as a hardcoded {@code @RestController}, so
 * host applications can opt out of exposing it.
 */
@Component
@ConditionalOnProperty(prefix = "dsl.reload", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DslReloadResource {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  public DslReloadResource() {
  }

  /**
   * Reloads DSL definitions from the configured source directory.
   */
  public ServerResponse reload(ServerRequest request) throws IOException {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_CONFIGURED", "dsl.source-dir is not configured", null, null, null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return error(HttpStatus.CONFLICT, new ErrorResponse(
              "NOT_FOUND", "Source directory does not exist: " + dir, null, null, null));
    }
    GlobalManager.globalManager().resetForTests();
    try {
      // TODO: add for reload a new special method, that uses a new classloader. Try to use here a
      // spi mechanizm
      new DefinitionLoader().load(dir, GlobalManager.globalManager());
      return ServerResponse.noContent().build();
    } catch (Exception e) {
      return error(HttpStatus.INTERNAL_SERVER_ERROR,
              new ErrorResponse("RELOAD_FAILED", e.getMessage(), null, null, null));
    }
  }

  private static ServerResponse error(HttpStatus status, ErrorResponse body) throws IOException {
    return ServerResponse.status(status).body(body);
  }
}
