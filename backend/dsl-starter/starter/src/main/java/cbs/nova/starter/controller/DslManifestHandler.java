package cbs.nova.starter.controller;

import cbs.nova.starter.service.PieceManifestService;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the piece-manifest reload endpoint. Registered as a {@code RouterFunction}
 * bean by {@link cbs.nova.starter.config.router.DslManifestRouterConfiguration} rather than as a
 * hardcoded {@code @RestController}, so host applications can opt out of exposing it when the
 * manifest loader is disabled.
 */
public class DslManifestHandler {

  private final PieceManifestService pieceManifestService;

  public DslManifestHandler(PieceManifestService pieceManifestService) {
    this.pieceManifestService = pieceManifestService;
  }

  /**
   * POST /api/dsl/manifest/reload — reloads the configured manifest, validates it, and atomically
   * swaps the in-memory lookup snapshot. The previous snapshot stays live if validation fails.
   */
  public ServerResponse reload(ServerRequest request) {
    return pieceManifestService.reload(request);
  }
}
