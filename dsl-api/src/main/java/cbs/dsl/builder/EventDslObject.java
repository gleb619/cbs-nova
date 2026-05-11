package cbs.dsl.builder;

import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import lombok.Builder;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

@Builder(toBuilder = true)
public record EventDslObject(
    String code,
    String name,
    List<ParameterDefinition> parameters,
    Function<Context, Context> contextBlock,
    Consumer<TransactionsScope> transactionsBlock,
    BiConsumer<FinishContext, Throwable> finishBlock)
    implements StandardDslObject {}
