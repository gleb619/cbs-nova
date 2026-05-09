package cbs.dsl.builder;

import cbs.dsl.api.ContextTypes.ContextInput;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/** Builder for creating transaction objects from DSL files. */
public class TransactionBuilder {

  private final String code;
  private String name;
  private final List<ParameterDefinition> parameters = new ArrayList<>();
  private final Consumer<ContextInput> contextBlock = _ -> {};
  private Function<TransactionInput, TransactionOutput> previewBlock;
  private Function<TransactionInput, TransactionOutput> executeBlock;
  private Function<TransactionInput, TransactionOutput> rollbackBlock;

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

  public TransactionBuilder context(Consumer<TransactionInput> block) {
    // this.contextBlock = block;
    return this;
  }

  public TransactionBuilder preview(Function<TransactionInput, TransactionOutput> block) {
    this.previewBlock = block;
    return this;
  }

  public TransactionBuilder execute(Function<TransactionInput, TransactionOutput> block) {
    this.executeBlock = block;
    return this;
  }

  public TransactionBuilder rollback(Function<TransactionInput, TransactionOutput> block) {
    this.rollbackBlock = block;
    return this;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public List<ParameterDefinition> getParameters() {
    return Collections.unmodifiableList(new ArrayList<>(parameters));
  }

  public Consumer<ContextInput> context() {
    return contextBlock;
  }

  public Function<TransactionInput, TransactionOutput> preview() {
    return previewBlock;
  }

  public Function<TransactionInput, TransactionOutput> execute() {
    return executeBlock;
  }

  public Function<TransactionInput, TransactionOutput> rollback() {
    return rollbackBlock;
  }

  public DslObject build() {
    return TransactionDslObject.builder()
        .code(code)
        .name(name)
        .parameters(Collections.unmodifiableList(new ArrayList<>(parameters)))
        .contextBlock(ContextInput::asOutput)
        .previewBlock(previewBlock)
        .executeBlock(executeBlock)
        .rollbackBlock(rollbackBlock)
        .build();
  }
}
