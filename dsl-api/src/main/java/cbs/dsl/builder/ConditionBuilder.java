package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.ConditionContext;
import cbs.dsl.api.context.Context;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builder for creating inline condition objects from DSL files. */
@Getter
public class ConditionBuilder {

  private final String code;
  private String name;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<Context, Context> contextBlock = Context::copy;
  private Function<ConditionContext, ConditionContext> checkBlock;

  ConditionBuilder(String code) {
    this.code = code;
  }

  public ConditionBuilder name(String name) {
    this.name = name;
    return this;
  }

  public ConditionBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public ConditionBuilder context(Function<Context, Context> block) {
    this.contextBlock = block;
    return this;
  }

  public ConditionBuilder check(Function<ConditionContext, ConditionContext> block) {
    this.checkBlock = block;
    return this;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Function<Context, Context> context() {
    return contextBlock;
  }

  public Function<ConditionContext, ConditionContext> check() {
    return checkBlock;
  }

  public DslObject build() {
    return ConditionDslObject.builder()
        .code(code)
        .name(name)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .contextBlock(contextBlock)
        .checkBlock(checkBlock)
        .build();
  }
}
