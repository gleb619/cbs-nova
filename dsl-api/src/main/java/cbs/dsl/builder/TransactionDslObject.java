package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for transactions — implements {@link TransactionDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class TransactionDslObject implements DslObject {

  private final String code;
  private final String name;
  private final List<ParameterDefinition> parameters;
  private final Consumer<TransactionContext> contextBlock;
  private final Function<TransactionContext, TransactionOutput> previewBlock;
  private final Function<TransactionContext, TransactionOutput> executeBlock;
  private final Function<TransactionContext, TransactionOutput> rollbackBlock;

}
