package cbs.dsl.evaluator;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.builder.HelperDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class Evaluator {

  private final RegistryHelperEvaluator helperEvaluator;

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
}
