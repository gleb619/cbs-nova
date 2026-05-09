package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import lombok.Builder;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@Builder(toBuilder = true)
public record EventDslObject(
    String code,
    List<ParameterDefinition> parameters,
    Consumer<EnrichmentContext> contextBlock,
    Consumer<DisplayScope> displayBlock,
    Consumer<TransactionsScope> transactionsBlock,
    List<String> transactionCodes,
    BiConsumer<FinishContext, Throwable> finishBlock)
    implements DslObject {

}
