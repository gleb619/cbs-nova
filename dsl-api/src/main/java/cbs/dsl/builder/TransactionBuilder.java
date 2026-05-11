package cbs.dsl.builder;

import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.TransactionContext;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builder for creating transaction objects from DSL files. */
@Getter
public class TransactionBuilder {

  private final String code;
  private String name;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private Function<Context, Context> contextBlock = Context::copy;
  private Function<TransactionContext, TransactionContext> previewBlock;
  private Function<TransactionContext, TransactionContext> executeBlock;
  private Function<TransactionContext, TransactionContext> rollbackBlock;

  TransactionBuilder(String code) {
    this.code = code;
  }

  public TransactionBuilder name(String name) {
    this.name = name;
    return this;
  }

  public TransactionBuilder parameters(Consumer<ParametersBuilder> block) {
    ParametersBuilder builder = new ParametersBuilder();
    block.accept(builder);
    this.parameters.addAll(builder.build());
    return this;
  }

  public TransactionBuilder context(Function<Context, Context> block) {
    this.contextBlock = block;
    return this;
  }

  public TransactionBuilder preview(Function<TransactionContext, TransactionContext> block) {
    this.previewBlock = block;
    return this;
  }

  public TransactionBuilder execute(Function<TransactionContext, TransactionContext> block) {
    this.executeBlock = block;
    return this;
  }

  public TransactionBuilder rollback(Function<TransactionContext, TransactionContext> block) {
    this.rollbackBlock = block;
    return this;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Function<Context, Context> context() {
    return contextBlock;
  }

  public Function<TransactionContext, TransactionContext> preview() {
    return previewBlock;
  }

  public Function<TransactionContext, TransactionContext> execute() {
    return executeBlock;
  }

  public Function<TransactionContext, TransactionContext> rollback() {
    return rollbackBlock;
  }

  public DslObject build() {
    return TransactionDslObject.builder()
        .code(code)
        .name(name)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .contextBlock(contextBlock)
        .previewBlock(previewBlock)
        .executeBlock(executeBlock)
        .rollbackBlock(rollbackBlock)
        .build();
  }
}
