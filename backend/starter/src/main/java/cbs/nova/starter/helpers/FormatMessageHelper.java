package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import org.jspecify.annotations.NonNull;

@Helper(name = "formatMessage")
public class FormatMessageHelper implements Executable<FormatMessageIn, FormatMessageOut> {

  @Override
  public @NonNull Result<FormatMessageOut> execute(@NonNull Context<FormatMessageIn> ctx) {
    FormatMessageIn input = ctx.body();
    if (input.template() == null) {
      return Result.failure(new IllegalArgumentException("template is required"));
    }
    String result = input.template();
    if (input.params() != null) {
      for (var entry : input.params().entrySet()) {
        result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
      }
    }
    return Result.success(new FormatMessageOut(result));
  }
}
