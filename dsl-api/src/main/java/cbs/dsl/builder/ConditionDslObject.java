package cbs.dsl.builder;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

@Builder(toBuilder = true)
public record ConditionDslObject(
    String code,
    List<ParameterDefinition> parameters,
    Function<ConditionInput, ConditionOutput> evaluateBlock)
    implements DslObject {

}
