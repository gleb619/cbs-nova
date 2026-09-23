package cbs.nova.dsl.model;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ExecutableDescriptor;
import org.jspecify.annotations.NonNull;

/**
 * Factory for {@link DslDescriptor} instances built from {@link ExecutableDescriptor} (helper)
 * descriptors, for sites that resolve a helper through {@code GlobalManager.describeHelper} and
 * need a full {@link DslDescriptor} (validation, preview cache keys, tests).
 *
 * <p>
 * The returned descriptor is immutable and function-typed; process/transaction-only fields
 * (taskQueue, version, timeouts) are left null. The {@code name} parameter is used verbatim —
 * callers pass exactly the name their site requires (helper name, lookup name, or a null-safe
 * variant), preserving pre-factory behavior.
 */
public final class Descriptors {

  private Descriptors() {
  }

  public static DslDescriptor from(@NonNull String name, @NonNull ExecutableDescriptor helper) {
    return DslDescriptor.builder()
            .objectDescriptor(new HelperObjectDescriptor(name, helper))
            .parameters(helper.parameters())
            .taskQueue(null)
            .version(null)
            .startToCloseTimeout(null)
            .heartbeatTimeout(null)
            .build();
  }

  private record HelperObjectDescriptor(@NonNull String name, @NonNull ExecutableDescriptor helper)
          implements
            ObjectDescriptor {

    @Override
    public @NonNull DslType type() {
      return DslType.FUNCTION;
    }

    @Override
    public String description() {
      return helper.description();
    }

    @Override
    public Class<?> inputType() {
      return helper.inputType();
    }

    @Override
    public Class<?> outputType() {
      return helper.outputType();
    }
  }
}
