package cbs.nova.starter.model;

import org.jspecify.annotations.Nullable;

/**
 * One row of the T551 manifest-guard snapshot: the allow/deny verdict for a single
 * {@code button}-target piece, resolved server-side for the current principal.
 *
 * <p>
 * Deliberately carries <b>no check internals</b> — no role names, no feature-flag names, no
 * rate-class names — only the verdict plus an optional short machine-readable reason. The frontend
 * renders verdicts as UX sugar; it never learns <em>why</em> a piece is denied, and the security
 * boundary stays server-side (T549 {@code PieceGuardFilter}).
 */
public record ManifestGuardEntry(String id, boolean allowed, @Nullable String reason) {

  public ManifestGuardEntry {
    if (id == null || id.isBlank()) {
      throw new IllegalArgumentException("id required");
    }
  }
}
