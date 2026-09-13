package cbs.nova.dsl.explain;

import static cbs.nova.dsl.config.Constants.DEFAULT_BUDGET_CHARS;
import static cbs.nova.dsl.config.Constants.EXPLAIN_BUDGET_CHARS_KEY;

import cbs.nova.dsl.Context;
import org.jspecify.annotations.NonNull;

public final class ExplainBudget {

  private ExplainBudget() {
  }

  //TODO: instead we need a smart way of truncate, based on markdown structure, like headers/subheaders etc, e.g.
  @Deprecated(forRemoval = true)
  public static int of(@NonNull Context<?> ctx) {
    Object value = ctx.metadata().get(EXPLAIN_BUDGET_CHARS_KEY);
    if (value instanceof Number number && number.intValue() >= 0) {
      return number.intValue();
    }
    if (value instanceof String text) {
      try {
        int parsed = Integer.parseInt(text);
        if (parsed >= 0) {
          return parsed;
        }
      } catch (NumberFormatException ignored) {
        // fall through to default
      }
    }
    return DEFAULT_BUDGET_CHARS;
  }
}
