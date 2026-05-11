package cbs.dsl.evaluator;

import cbs.dsl.api.DefinitionRegistry;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.api.context.TransactionEvaluator;
import cbs.dsl.builder.TransactionDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Evaluates a {@link TransactionDslObject} DSL descriptor at runtime.
 *
 * <p>Interprets the preview, execute, and rollback blocks directly. Holds a reference to the
 * {@link DefinitionRegistry} so that nested transaction resolution can be performed.
 */
@RequiredArgsConstructor
public class RegistryTransactionEvaluator implements TransactionEvaluator {

  private final DefinitionRegistry registry;

  /**
   * Resolves a transaction definition by code from the registry.
   *
   * @param code the transaction code
   * @return the transaction definition
   * @throws IllegalArgumentException if not found
   */
  @NonNull
  public TransactionDefinition resolveTransaction(@NonNull String code) {
    return registry.resolveTransaction(code);
  }

  /**
   * Evaluates the preview block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction context result
   */
  public TransactionContext evaluatePreview(
      @NonNull TransactionDslObject dsl, @NonNull TransactionContext ctx) {
    var result = dsl.previewBlock().apply(ctx);
    if (result instanceof TransactionContext tctx) {
      return tctx.copy();
    } else {
      var values = new HashMap<>(ctx.params());
      values.put(dsl.code(), values);
      return ctx.toBuilder().params(values).build();
    }
  }

  /**
   * Evaluates the execute block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction context result
   */
  public TransactionContext evaluateExecute(
      @NonNull TransactionDslObject dsl, @NonNull TransactionContext ctx) {
    var result = dsl.executeBlock().apply(ctx);
    if (result instanceof TransactionContext tctx) {
      return tctx.copy();
    } else {
      var values = new HashMap<>(ctx.params());
      values.put(dsl.code(), values);
      return ctx.toBuilder().params(values).build();
    }
  }

  /**
   * Evaluates the rollback block of the given transaction DSL object.
   *
   * @param dsl the transaction DSL object
   * @param ctx the transaction context
   * @return the transaction context result
   */
  public TransactionContext evaluateRollback(
      @NonNull TransactionDslObject dsl, @NonNull TransactionContext ctx) {
    var result = dsl.rollbackBlock().apply(ctx);
    if (result instanceof TransactionContext tctx) {
      return tctx.copy();
    } else {
      var values = new HashMap<>(ctx.params());
      values.put(dsl.code(), values);
      return ctx.toBuilder().params(values).build();
    }
  }

  @Override
  public <U> U evaluate(String code, Map<String, Object> params) {
    // TODO: how to call preview here?
    TransactionOutput output = resolveTransaction(code).execute(TransactionInput.from(params));
    if (output.params().size() == 1) {
      var entry = output.params().entrySet().iterator().next();

      return (U) entry.getValue();
    }

    return (U) output.params();
  }
}
