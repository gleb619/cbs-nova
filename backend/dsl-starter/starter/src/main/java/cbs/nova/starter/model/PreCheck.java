package cbs.nova.starter.model;

import java.util.List;
import java.util.Objects;

/**
 * Discriminated union of pre-execution checks. All checks in a piece's {@code preCheck} array must
 * pass before the guarded action is allowed to run.
 */
public sealed interface PreCheck {

  String type();

  /**
   * Role check: caller must satisfy at least one of the named
   * {@link cbs.nova.starter.security.Role} values.
   */
  record RoleCheck(List<String> anyOf) implements PreCheck {

    public RoleCheck {
      Objects.requireNonNull(anyOf, "anyOf required");
      anyOf = List.copyOf(anyOf);
    }

    @Override
    public String type() {
      return "role";
    }
  }

  /**
   * Feature-flag check: the named flag must be enabled.
   */
  record FeatureFlagCheck(String flag) implements PreCheck {

    public FeatureFlagCheck {
      Objects.requireNonNull(flag, "flag required");
    }

    @Override
    public String type() {
      return "feature-flag";
    }
  }

  /**
   * Rate-class check: assigns the piece to a named rate-limit class. The YAML key is {@code class};
   * the Java component is named {@code rateClass} because {@code class} is a reserved word.
   */
  record RateClassCheck(String rateClass) implements PreCheck {

    public RateClassCheck {
      Objects.requireNonNull(rateClass, "rateClass required");
    }

    @Override
    public String type() {
      return "rate-class";
    }
  }
}
