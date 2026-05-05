package cbs.dsl.builder;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.TransactionContext;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Concrete DSL object for conditions — implements {@link ConditionDefinition} and {@link DslObject}. */
@Getter
@RequiredArgsConstructor
public class ConditionDslObject implements DslObject {

  private final String code;
  private final List<ParameterDefinition> parameters;
  private final Function<TransactionContext, ConditionOutput> evaluateBlock;

}
