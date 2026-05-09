package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.ParameterDefinition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builder for a single helper. Used internally by {@link HelpersBuilder}. */
public class HelperBuilder {

  private final String code;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<HelperInput, HelperOutput> previewBlock;
  private Function<HelperInput, HelperOutput> executeBlock;

  HelperBuilder(String code) {
    this.code = code;
  }

  public HelperBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public HelperBuilder preview(Function<HelperInput, HelperOutput> block) {
    this.previewBlock = block;
    return this;
  }

  public HelperBuilder execute(Function<HelperInput, HelperOutput> block) {
    this.executeBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Function<HelperInput, HelperOutput> preview() {
    return previewBlock;
  }

  public Function<HelperInput, HelperOutput> execute() {
    return executeBlock;
  }

  DslObject build() {
    return HelperDslObject.builder()
        .code(code)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .previewBlock(previewBlock)
        .executeBlock(executeBlock)
        .build();
  }
}
