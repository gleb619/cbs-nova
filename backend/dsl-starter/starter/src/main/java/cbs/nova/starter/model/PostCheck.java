package cbs.nova.starter.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Discriminated union of post-execution hooks. Hooks run only after the guarded action has
 * completed successfully.
 */
public sealed interface PostCheck {

  String type();

  /**
   * Writes an audit row with the given action code.
   */
  record AuditWriteCheck(String action) implements PostCheck {

    public AuditWriteCheck {
      Objects.requireNonNull(action, "action required");
    }

    @Override
    public String type() {
      return "audit-write";
    }
  }

  /**
   * Asserts a post-condition. Both fields are optional at parse time; semantics are defined by the
   * enforcement layer (T550).
   */
  record InvariantAssertCheck(@Nullable String expr,
          @Nullable String description) implements PostCheck {

    @Override
    public String type() {
      return "invariant-assert";
    }
  }

  /**
   * Emits a best-effort notification to the named channel.
   */
  record NotifyCheck(String channel) implements PostCheck {

    public NotifyCheck {
      Objects.requireNonNull(channel, "channel required");
    }

    @Override
    public String type() {
      return "notify";
    }
  }
}
