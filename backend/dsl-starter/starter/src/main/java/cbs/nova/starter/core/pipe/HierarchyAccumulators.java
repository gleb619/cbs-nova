package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.config.Constants;
import cbs.nova.dsl.model.HierarchyAccumulator;
import java.util.Optional;
import org.jspecify.annotations.NonNull;

public final class HierarchyAccumulators {

  private HierarchyAccumulators() {
  }

  public static @NonNull Optional<HierarchyAccumulator> resolve(
          @NonNull DslPipeContext context) {
    Object value = context.dslContext().metadata()
            .get(Constants.HIERARCHY_GRAPH_ACCUMULATOR_KEY);
    return Optional.ofNullable((HierarchyAccumulator) value);
  }
}
