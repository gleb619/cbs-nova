package cbs.dsl.evaluator;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.HelperContext;
import cbs.dsl.api.context.EventContext;
import cbs.dsl.api.TransactionTypes;
import cbs.dsl.api.context.TransactionContext;
import cbs.dsl.builder.TransactionDslObject;
import cbs.dsl.builder.EventDslObject;
import cbs.dsl.builder.HelperDslObject;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class Evaluator {

  private static Evaluator instance;

  private final RegistryEventEvaluator eventEvaluator;
  private final RegistryHelperEvaluator helperEvaluator;
  private final RegistryTransactionEvaluator transactionEvaluator;
  // TODO: create a context Evaluator for `.context` block

  public static Evaluator getInstance() {
    if (instance == null) {
      throw new IllegalStateException(
          "Evaluator not initialized. Call setInstance(Evaluator) during startup.");
    }
    return instance;
  }

  public static synchronized void setInstance(Evaluator evaluator) {
    instance = evaluator;
  }

  @NonNull
  public HelperOutput previewHelper(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    HelperContext ctx = helperEvaluator.evaluatePreview(dsl, input);
    return new HelperOutput(ctx.params());
  }

  @NonNull
  public HelperOutput executeHelper(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    HelperContext ctx = helperEvaluator.evaluateExecute(dsl, input);
    return new HelperOutput(ctx.params());
  }

  @NonNull
  public Context evaluateContext(@NonNull StandardDslObject dsl, @NonNull Object ctx) {
    if (ctx instanceof Context c && dsl.contextBlock() != null) {
      return dsl.contextBlock().apply(c);
    }
    return null;
  }

  @NonNull
  public Context evaluateContext(@NonNull HelperDslObject dsl, Map<String, Object> params) {
    Context context = Context.builder()
        .params(new HashMap<>(params))
        .helperEvaluator(helperEvaluator)
        .build();
    if (dsl.contextBlock() != null) {
      return dsl.contextBlock().apply(context);
    }
    return context;
  }

  public TransactionTypes.TransactionOutput evaluateExecute(TransactionDslObject dsl, TransactionTypes.TransactionInput input) {
    TransactionContext ctx = transactionEvaluator.evaluateExecute(dsl, TransactionContext.builder().params(new HashMap<>(input.params())).build());
    return TransactionTypes.TransactionOutput.success(ctx.params());
  }

  public TransactionTypes.TransactionOutput evaluateRollback(TransactionDslObject dsl, TransactionTypes.TransactionInput input) {
    TransactionContext ctx = transactionEvaluator.evaluateRollback(dsl, TransactionContext.builder().params(new HashMap<>(input.params())).build());
    return TransactionTypes.TransactionOutput.success(ctx.params());
  }
  public EventContext evaluateEvent(@NonNull EventDslObject dsl, @NonNull EventInput input) {
    return eventEvaluator.evaluateTransactions(dsl, input);
  }
}
