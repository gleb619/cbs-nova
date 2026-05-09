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
import lombok.Builder;

import java.util.List;
import java.util.function.Consumer;

@Builder(toBuilder = true)
public record MassOperationDslObject(
    String code,
    String category,
    List<ParameterDefinition> parameters,
    List<TriggerDefinition> triggers,
    SourceDefinition source,
    LockDefinition lock,
    Consumer<MassOperationContext> contextBlock,
    Consumer<MassOperationContext> itemBlock,
    Consumer<SignalTypes.Signal> onPartial,
    Consumer<SignalTypes.Signal> onCompleted)
    implements DslObject {

}
