package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.EventTypes.EventOutput;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.DisplayScope;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;

import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for events — implements {@link EventDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class EventDslObject implements DslObject {

  private final String code;
  private final List<ParameterDefinition> parameters;
  private final Consumer<EnrichmentContext> contextBlock;
  private final Consumer<DisplayScope> displayBlock;
  private final Consumer<TransactionsScope> transactionsBlock;
  private final List<String> transactionCodes;
  private final BiConsumer<FinishContext, Throwable> finishBlock;

}
