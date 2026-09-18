package cbs.nova.starter.controller;

import cbs.nova.starter.model.ManifestGuardEntry;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PreCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.PieceManifestService;
import java.util.List;
import java.util.Locale;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional handler for the piece-manifest endpoints. Registered as a {@code RouterFunction} bean
 * by {@link cbs.nova.starter.config.router.DslManifestRouterConfiguration} rather than as a
 * hardcoded {@code RestController}, so host applications can opt out of exposing it when the
 * manifest loader is disabled.
 */
public class DslManifestHandler {

  /**
   * Type probe for {@link PieceManifestService#byTarget(Target)} — only {@link #type()} is read.
   */
  private static final Target BUTTON_TARGET_PROBE = new Target.ButtonTarget("");

  private final PieceManifestService pieceManifestService;
  private final RoleResolver roleResolver;

  public DslManifestHandler(PieceManifestService pieceManifestService, RoleResolver roleResolver) {
    this.pieceManifestService = pieceManifestService;
    this.roleResolver = roleResolver;
  }

  /**
   * POST /api/dsl/manifest/reload — reloads the configured manifest, validates it, and atomically
   * swaps the in-memory lookup snapshot. The previous snapshot stays live if validation fails.
   */
  public ServerResponse reload(ServerRequest request) {
    return pieceManifestService.reload(request);
  }

  /**
   * GET /api/dsl/manifest/guard — read-only snapshot of every {@code button}-target piece resolved
   * to an allow/deny verdict for the current principal (T551). The response carries only
   * {@code {id, allowed, reason?}} rows — never {@code preCheck}/{@code postCheck} internals.
   *
   * <p>
   * Resolution reuses the T549 role source: the caller's {@link Role} comes from the same
   * {@link RoleResolver} bean {@code PieceGuardFilter} uses, and role satisfaction uses the same
   * {@link Role#satisfies(Role)} hierarchy check as the guard's role pre-check evaluation. Only
   * {@code role}-type preChecks factor into this snapshot: {@code feature-flag} and
   * {@code rate-class} checks are server-enforcement-time concerns (a flag may flip or a bucket may
   * drain between page load and button click), so a button gated only by those resolves
   * {@code allowed=true} here and is still enforced at click time server-side.
   *
   * <p>
   * This endpoint is defense-in-depth UX support for the frontend button guard; it does not replace
   * {@code PieceGuardFilter} enforcement and grants no access by itself.
   */
  public ServerResponse guard(ServerRequest request) {
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(resolveGuard(request));
  }

  /**
   * Resolves the guard snapshot rows for the current principal; separated from {@link #guard} so
   * tests can assert the entries without servlet response plumbing.
   */
  List<ManifestGuardEntry> resolveGuard(ServerRequest request) {
    Role caller = roleResolver.resolve(request.servletRequest());
    return pieceManifestService.byTarget(BUTTON_TARGET_PROBE).stream()
            .map(piece -> resolve(piece, caller))
            .toList();
  }

  private static ManifestGuardEntry resolve(Piece piece, Role caller) {
    for (PreCheck check : piece.preCheck()) {
      if (check instanceof PreCheck.RoleCheck roleCheck && !satisfies(roleCheck, caller)) {
        return new ManifestGuardEntry(piece.id(), false, "role");
      }
    }
    return new ManifestGuardEntry(piece.id(), true, null);
  }

  /**
   * Mirrors {@code PieceGuardFilter}'s role pre-check evaluation ({@code anyOf} + rank hierarchy).
   */
  private static boolean satisfies(PreCheck.RoleCheck check, Role caller) {
    for (String name : check.anyOf()) {
      Role required = Role.valueOf(name.toUpperCase(Locale.ROOT));
      if (caller.satisfies(required)) {
        return true;
      }
    }
    return false;
  }
}
