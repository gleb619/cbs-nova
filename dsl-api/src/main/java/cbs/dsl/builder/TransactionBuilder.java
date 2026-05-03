package cbs.dsl.builder;

import cbs.dsl.api.DslDefinitionCollector;
import cbs.dsl.api.ParameterDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Builder for creating inline {@link TransactionDefinition} instances from DSL files.
 *
 * <p>Usage:
 *
 * <pre>{@code
 * TransactionDefinition tx = TransactionDsl.transaction("DEBIT_ACCOUNT")
 *     .requiredParam("accountCode")
 *     .execute(ctx -> new TransactionOutput(Map.of("txId", "123")))
 *     .build();
 * }</pre>
 */
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

  public TransactionDefinition build() {
    List<ParameterDefinition> params = Collections.unmodifiableList(new ArrayList<>(parameters));
    TransactionDefinition def = new TransactionDefinition() {
      @Override
      public String getCode() {
        return code;
      }

      @Override
      public String getName() {
        return name;
      }

      @Override
      public List<ParameterDefinition> getParameters() {
        return params;
      }

      @Override
      public Consumer<TransactionContext> getContextBlock() {
        return contextBlock;
      }

      @Override
      public TransactionOutput preview(TransactionInput input) {
        if (previewBlock == null) {
          return TransactionOutput.empty();
        }
        TransactionContext ctx = toContext(input);
        contextBlock.accept(ctx);
        return previewBlock.apply(ctx);
      }

      @Override
      public TransactionOutput execute(TransactionInput input) {
        if (executeBlock == null) {
          return TransactionOutput.empty();
        }
        TransactionContext ctx = toContext(input);
        contextBlock.accept(ctx);
        return executeBlock.apply(ctx);
      }

      @Override
      public TransactionOutput rollback(TransactionInput input) {
        if (rollbackBlock == null) {
          return TransactionOutput.empty();
        }
        TransactionContext ctx = toContext(input);
        contextBlock.accept(ctx);
        return rollbackBlock.apply(ctx);
      }

      private TransactionContext toContext(TransactionInput input) {
        return new TransactionContext(
            input.eventCode(),
            input.eventNumber(),
            null,
            "dev",
            input.params() != null ? input.params() : Collections.emptyMap(),
            false);
      }
    };
    DslDefinitionCollector.register(def);
    return def;
  }
}
