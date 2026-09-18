package cbs.nova.starter.model;

import java.util.Locale;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Discriminated union of post-execution hooks. Hooks run only after the guarded action has
 * completed successfully.
 *
 * <p>
 * Each hook carries its own {@code onFailure} policy ({@link #ON_FAILURE_WARN warn} (default) or
 * {@link #ON_FAILURE_BLOCK block-next-execution}) — see the enforcement semantics in
 * {@code docs/vhs-manifest.md}.
 */
public sealed interface PostCheck {

  String type();

  /** Per-hook failure policy: {@link #ON_FAILURE_WARN warn} or {@link #ON_FAILURE_BLOCK}. */
  String onFailure();

  /** Failure policy: log + audit a FAILURE row; the hook failure never affects anything else. */
  String ON_FAILURE_WARN = "warn";
  /** Failure policy: as {@code warn}, plus block further executions of the piece until cleared. */
  String ON_FAILURE_BLOCK = "block-next-execution";

  /** Normalizes a raw {@code onFailure} value, defaulting blank to {@code warn}. */
  static String normalizeOnFailure(@Nullable String onFailure) {
    if (onFailure == null || onFailure.isBlank()) {
      return ON_FAILURE_WARN;
    }
    return onFailure.trim().toLowerCase(Locale.ROOT);
  }

  /**
   * Writes an audit row with the given action code.
   */
  record AuditWriteCheck(String action, String onFailure) implements PostCheck {

    public AuditWriteCheck {
      Objects.requireNonNull(action, "action required");
      onFailure = normalizeOnFailure(onFailure);
    }

    public AuditWriteCheck(String action) {
      this(action, null);
    }

    @Override
    public String type() {
      return "audit-write";
    }
  }

  /**
   * Asserts a post-condition. Both fields are optional at parse time; the supported named
   * conditions are defined by the T550 enforcement layer (see {@code InvariantAssertHook}).
   */
  record InvariantAssertCheck(@Nullable String expr,
          @Nullable String description,
          String onFailure) implements PostCheck {

    public InvariantAssertCheck {
      onFailure = normalizeOnFailure(onFailure);
    }

    public InvariantAssertCheck(@Nullable String expr, @Nullable String description) {
      this(expr, description, null);
    }

    @Override
    public String type() {
      return "invariant-assert";
    }
  }

  /**
   * Emits a best-effort notification to the named channel.
   */
  record NotifyCheck(String channel, String onFailure) implements PostCheck {

    public NotifyCheck {
      Objects.requireNonNull(channel, "channel required");
      onFailure = normalizeOnFailure(onFailure);
    }

    public NotifyCheck(String channel) {
      this(channel, null);
    }

    @Override
    public String type() {
      return "notify";
    }
  }
}
