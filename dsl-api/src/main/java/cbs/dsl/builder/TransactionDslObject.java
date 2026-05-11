package cbs.dsl.builder;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.TransactionContext;
import lombok.Builder;

import java.util.List;
import java.util.function.Function;

@Builder(toBuilder = true)
public record TransactionDslObject(
    String code,
    String name,
    List<ParameterDefinition> parameters,
    Function<Context, Context> contextBlock,
    Function<TransactionContext, TransactionContext> previewBlock,
    Function<TransactionContext, TransactionContext> executeBlock,
    Function<TransactionContext, TransactionContext> rollbackBlock)
    implements StandardDslObject {}
