package cbs.nova.starter.controller;

import cbs.nova.starter.exception.DefinitionNotFoundException;
import cbs.nova.starter.model.DefinitionSnapshotDto;
import cbs.nova.starter.service.DefinitionSnapshotService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the definition source snapshot endpoints (T875). Registered as a
 * {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.router.DslDefinitionSnapshotRouterConfiguration}.
 *
 * <p>
 * {@code GET /api/dsl/definitions/{name}/versions} lists the captured snapshots newest first;
 * {@code GET /api/dsl/definitions/{name}/versions/{hash}} returns the stored source text plus a
 * unified line diff against the previous snapshot. An unknown hash throws
 * {@link DefinitionNotFoundException}, mapped to a 404 {@code NOT_FOUND} envelope by
 * {@link DslExceptionHandler}.
 */
@Component
@RequiredArgsConstructor
public class DslDefinitionSnapshotHandler {

  private final DefinitionSnapshotService snapshotService;

  public ServerResponse versions(ServerRequest request) {
    String name = request.pathVariable("name");
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(snapshotService.versions(name));
  }

  public ServerResponse version(ServerRequest request) {
    String name = request.pathVariable("name");
    String hash = request.pathVariable("hash");
    DefinitionSnapshotDto snapshot = snapshotService.find(name, hash)
            .orElseThrow(() -> new DefinitionNotFoundException(
                    "definition " + name + " snapshot " + hash));
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(snapshot);
  }
}
