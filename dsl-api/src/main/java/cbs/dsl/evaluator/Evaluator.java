package cbs.dsl.evaluator;

import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.builder.HelperDslObject;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public class Evaluator {

  private final HelperEvaluator helperEvaluator;

  @NonNull
  public HelperOutput previewHelper(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    return helperEvaluator.evaluatePreview(dsl, input);
  }

  @NonNull
  public HelperOutput executeHelper(@NonNull HelperDslObject dsl, @NonNull HelperInput input) {
    return helperEvaluator.evaluateExecute(dsl, input);
  }

}
