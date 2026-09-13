package cbs.nova.dsl;

import org.jspecify.annotations.NonNull;

/**
 * Contract for producing a human- or AI-oriented explanation of an executable entity.
 * <p>
 * The textual budget (description plus any diagram) is carried by the context metadata under
 * {@value cbs.nova.dsl.config.Constants#EXPLAIN_BUDGET_CHARS_KEY}; implementations must truncate
 * rather than exceed it, falling back to
 * {@value cbs.nova.dsl.config.Constants#DEFAULT_BUDGET_CHARS} when the metadata is absent or
 * invalid.
 * </p>
 */
@FunctionalInterface
public interface ExplainSupport<IN, OUT> {

  @NonNull
  OUT explain(@NonNull Context<IN> ctx);
}
