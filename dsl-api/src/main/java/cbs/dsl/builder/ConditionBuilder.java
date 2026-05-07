package cbs.dsl.builder;

import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.TransactionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Builder for creating inline condition objects from DSL files. */
public class ConditionBuilder {

  private final String code;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<TransactionContext, ConditionOutput> evaluateBlock;

  ConditionBuilder(String code) {
    this.code = code;
  }

  public ConditionBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public ConditionBuilder evaluate(Function<TransactionContext, ConditionOutput> block) {
    this.evaluateBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Function<TransactionContext, ConditionOutput> evaluate() {
    return evaluateBlock;
  }

  public Predicate<TransactionContext> getPredicate() {
    return ctx -> {
      if (evaluateBlock == null) {
        return false;
      }
      return evaluateBlock.apply(ctx).result();
    };
  }

  public DslObject build() {
    List<ParameterDefinition> params = Collections.unmodifiableList(new ArrayList<>(parameters));

    DslObject obj = new ConditionDslObject(
        code,
        params,
        evaluateBlock);
    DslDefinitionCollector.register(obj);
    return obj;
  }
}
