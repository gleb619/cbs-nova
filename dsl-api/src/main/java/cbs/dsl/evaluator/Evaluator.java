package cbs.dsl.evaluator;

import cbs.dsl.api.EventTypes.EventInput;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.StandardDslObject;
import cbs.dsl.api.context.Context;
import cbs.dsl.api.context.EventContext;
import cbs.dsl.builder.EventDslObject;
import cbs.dsl.builder.HelperDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class Evaluator {

  private static Evaluator instance;

  private final RegistryEventEvaluator eventEvaluator;
  private final RegistryHelperEvaluator helperEvaluator;
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
    helperEvaluator.evaluatePreview(dsl, input);
    // TODO: fix bug
    return null;
  }

  @NonNull
  public HelperOutput executeHelper(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    helperEvaluator.evaluateExecute(dsl, input);
    // TODO: fix bug
    return null;
  }

  public Context evaluateContext(@NonNull StandardDslObject dsl, @NonNull Object ctx) {
    // TODO: add `.context` block evaluation here
    // dsl.contextBlock().apply(ctx)
    return null;
  }

  public EventContext evaluateEvent(@NonNull EventDslObject dsl, @NonNull EventInput input) {
    return eventEvaluator.evaluateTransactions(dsl, input);
  }
}
