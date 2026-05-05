package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.LockDefinition;
import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.MassOperationTypes.MassOperationInput;
import cbs.dsl.api.MassOperationTypes.MassOperationOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.SignalTypes;
import cbs.dsl.api.SourceDefinition;
import cbs.dsl.api.TriggerDefinition;
import cbs.dsl.api.context.MassOperationContext;

import java.util.List;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for mass operations — implements {@link MassOperationDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class MassOperationDslObject implements DslObject {

  private final String code;
  private final String category;
  private final List<ParameterDefinition> parameters;
  private final List<TriggerDefinition> triggers;
  private final SourceDefinition source;
  private final LockDefinition lock;
  private final Consumer<MassOperationContext> contextBlock;
  private final Consumer<MassOperationContext> itemBlock;
  private final Consumer<SignalTypes.Signal> onPartial;
  private final Consumer<SignalTypes.Signal> onCompleted;

}
