package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.ExplainGraphAccumulator;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public final class ExplainGraphAccumulators {

  private ExplainGraphAccumulators() {
  }

  public static @NonNull Optional<ExplainGraphAccumulator> resolve(
          @NonNull DslPipeContext context) {
    Object value = context.dslContext().metadata()
            .get(Constants.EXPLAIN_GRAPH_ACCUMULATOR_KEY);
    return Optional.ofNullable((ExplainGraphAccumulator) value);
  }
}
