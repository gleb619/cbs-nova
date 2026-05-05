package cbs.dsl.builder;

import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.DslObject;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

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
  private Consumer<TransactionContext> contextBlock = ctx -> {};
  private Function<TransactionContext, TransactionOutput> previewBlock;
  private Function<TransactionContext, TransactionOutput> executeBlock;
  private Function<TransactionContext, TransactionOutput> rollbackBlock;

  TransactionBuilder(String code) {
    this.code = code;
  }

  public TransactionBuilder name(String name) {
    this.name = name;
    return this;
  }

  public TransactionBuilder requiredParam(String name) {
    this.parameters.add(new ParameterDefinition(name, true));
    return this;
  }

  public TransactionBuilder optionalParam(String name) {
    this.parameters.add(new ParameterDefinition(name, false));
    return this;
  }

  public TransactionBuilder context(Consumer<TransactionContext> block) {
    this.contextBlock = block;
    return this;
  }

  public TransactionBuilder preview(Function<TransactionContext, TransactionOutput> block) {
    this.previewBlock = block;
    return this;
  }

  public TransactionBuilder execute(Function<TransactionContext, TransactionOutput> block) {
    this.executeBlock = block;
    return this;
  }

  public TransactionBuilder rollback(Function<TransactionContext, TransactionOutput> block) {
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

  public Consumer<TransactionContext> context() {
    return contextBlock;
  }

  public Function<TransactionContext, TransactionOutput> preview() {
    return previewBlock;
  }

  public Function<TransactionContext, TransactionOutput> execute() {
    return executeBlock;
  }

  public Function<TransactionContext, TransactionOutput> rollback() {
    return rollbackBlock;
  }

  public DslObject build() {
    List<ParameterDefinition> params = Collections.unmodifiableList(new ArrayList<>(parameters));

    DslObject obj = new TransactionDslObject(
        code,
        name,
        params,
        contextBlock,
        previewBlock,
        executeBlock,
        rollbackBlock);
    DslDefinitionCollector.register(obj);
    return obj;
  }
}
