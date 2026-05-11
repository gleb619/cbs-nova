package cbs.dsl.builder;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.context.ConditionContext;
import cbs.dsl.api.context.Context;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

@Builder(toBuilder = true)
public record ConditionDslObject(
    String code,
    String name,
    List<ParameterDefinition> parameters,
    Function<Context, Context> contextBlock,
    Function<ConditionContext, ConditionContext> checkBlock)
    implements StandardDslObject {}
