package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.HelperContext;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builder for a single helper. Used internally by {@link HelpersBuilder}. */
public class HelperBuilder {

  @Getter
  private final String code;

  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<Context, Context> contextBlock = Context::copy;
  private Function<HelperContext, HelperContext> previewBlock;
  private Function<HelperContext, HelperContext> executeBlock;

  HelperBuilder(String code) {
    this.code = code;
  }

  public HelperBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public HelperBuilder context(Function<Context, Context> block) {
    this.contextBlock = block;
    return this;
  }

  public HelperBuilder preview(Function<HelperContext, HelperContext> block) {
    this.previewBlock = block;
    return this;
  }

  public HelperBuilder execute(Function<HelperContext, HelperContext> block) {
    this.executeBlock = block;
    return this;
  }

  DslObject build() {
    return HelperDslObject.builder()
        .code(code)
        .parameters(List.copyOf(parameters))
        .contextBlock(contextBlock)
        .previewBlock(previewBlock)
        .executeBlock(executeBlock)
        .build();
  }
}
