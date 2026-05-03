package cbs.dsl.api;

import cbs.dsl.api.context.MassOperationContext;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Defines a mass operation — a batch process that loads a dataset, processes each item, and emits
 * completion signals.
 *
 * <p>Implementations are typically created via the Kotlin DSL {@code massOperation { }} block or
 * annotated with {@link DslComponent} for compile-time registration.
 */
public interface MassOperationDefinition {

  /** Canonical code used to look up this mass operation in the registry. */
  String getCode();

  /**
   * List of parameter definitions declared in the {@code parameters { }} block. Used for validation
   * and documentation purposes.
   */
  default List<ParameterDefinition> getParameters() {
    return Collections.emptyList();
  }

  /** Category used for grouping and scheduling. */
  String getCategory();

  /** Triggers that determine when this mass operation runs. */
  List<TriggerDefinition> getTriggers();

  /** Source that loads the dataset to process. */
  SourceDefinition getSource();

  /**
   * Optional lock definition. When present and {@link LockDefinition#isLocked} returns {@code
   * true}, the operation is deferred until the lock is released.
   */
  default LockDefinition getLock() {
    return null;
  }

  /** Optional context enrichment block that runs before loading the dataset. */
  default Consumer<MassOperationContext> getContextBlock() {
    return ctx -> {};
  }

  /** Block executed for each item in the dataset. */
  Consumer<MassOperationContext> getItemBlock();

  /** Callback invoked when a partial completion signal is received. */
  default Consumer<SignalTypes.Signal> getOnPartial() {
    return null;
  }

  /** Callback invoked when the operation is fully completed. */
  default Consumer<SignalTypes.Signal> getOnCompleted() {
    return null;
  }

  /** Executes the mass operation with the given input. */
  MassOperationTypes.MassOperationOutput execute(MassOperationTypes.MassOperationInput input);
}
