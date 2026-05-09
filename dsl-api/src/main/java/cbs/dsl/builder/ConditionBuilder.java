package cbs.dsl.builder;

import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;

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
  private Function<ConditionInput, ConditionOutput> evaluateBlock;

  ConditionBuilder(String code) {
    this.code = code;
  }

  public ConditionBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public ConditionBuilder evaluate(Function<ConditionInput, ConditionOutput> block) {
    this.evaluateBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Function<ConditionInput, ConditionOutput> evaluate() {
    return evaluateBlock;
  }

  public Predicate<ConditionInput> getPredicate() {
    return ctx -> {
      if (evaluateBlock == null) {
        return false;
      }
      return evaluateBlock.apply(ctx).result();
    };
  }

  public DslObject build() {
    return ConditionDslObject.builder()
        .code(code)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .evaluateBlock(evaluateBlock)
        .build();
  }
}
