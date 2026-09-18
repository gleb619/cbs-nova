package cbs.nova.starter.model;

import java.util.Objects;

/**
 * Discriminated union of piece target types. The YAML shape is a nested object with a {@code type}
 * field and type-specific siblings; each subtype exposes {@link #type()} for the dispatcher.
 */
public sealed interface Target {

  String type();

  /**
   * Guards an HTTP route. Route format is {@code METHOD path}, e.g. {@code POST /api/dsl/reload}.
   */
  record ApiTarget(String route) implements Target {

    public ApiTarget {
      Objects.requireNonNull(route, "route required");
    }

    @Override
    public String type() {
      return "api";
    }
  }

  /**
   * Guards a frontend button via its component key / {@code data-testid}.
   */
  record ButtonTarget(String uiKey) implements Target {

    public ButtonTarget {
      Objects.requireNonNull(uiKey, "uiKey required");
    }

    @Override
    public String type() {
      return "button";
    }
  }

  /**
   * Allowlists or guards a DSL object (helper, process, or function).
   */
  record ObjectTarget(String objectType, String objectName) implements Target {

    public ObjectTarget {
      Objects.requireNonNull(objectType, "objectType required");
      Objects.requireNonNull(objectName, "objectName required");
    }

    @Override
    public String type() {
      return "object";
    }
  }
}
