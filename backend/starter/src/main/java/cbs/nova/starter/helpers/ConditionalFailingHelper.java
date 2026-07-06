package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.ConditionalFailIn;
import cbs.nova.starter.helpers.model.ConditionalFailOut;
import org.jspecify.annotations.NonNull;

@Helper(name = "conditionalFailing")
public class ConditionalFailingHelper implements Executable<ConditionalFailIn, ConditionalFailOut> {

  @Override
  public @NonNull Result<ConditionalFailOut> execute(@NonNull Context<ConditionalFailIn> ctx) {
    ConditionalFailIn input = ctx.body();
    if (input.shouldFail()) {
      String reason = input.reason() != null
              ? input.reason()
              : "conditionalFailing helper triggered failure";
      return Result.failure(new RuntimeException(reason));
    }
    return Result.success(new ConditionalFailOut("ok"));
  }
}
