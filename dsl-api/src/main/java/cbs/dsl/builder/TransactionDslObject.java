package cbs.dsl.builder;

import cbs.dsl.api.ContextTypes.ContextInput;
import cbs.dsl.api.ContextTypes.ContextOutput;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

import java.util.List;
import java.util.function.Function;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for transactions — implements {@link TransactionDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class TransactionDslObject implements StandardDslObject {

  private final String code;
  private final String name;
  private final List<ParameterDefinition> parameters;
  private final Function<ContextInput, ContextOutput> contextBlock;
  private final Function<TransactionContext, TransactionOutput> previewBlock;
  private final Function<TransactionContext, TransactionOutput> executeBlock;
  private final Function<TransactionContext, TransactionOutput> rollbackBlock;

  @Override
  public String code() {
    return getCode();
  }

  @Override
  public String name() {
    return getName();
  }

  @Override
  public List<ParameterDefinition> parameters() {
    return getParameters();
  }

  @Override
  public Function<ContextInput, ContextOutput> contextBlock() {
    return getContextBlock();
  }
}
