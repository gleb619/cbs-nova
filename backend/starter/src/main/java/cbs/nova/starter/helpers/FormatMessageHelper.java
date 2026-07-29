package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import org.jspecify.annotations.NonNull;

import java.util.Map;

@Helper(name = "formatMessage")
public class FormatMessageHelper implements Executable<FormatMessageIn, FormatMessageOut> {

  @Override
  public @NonNull Result<FormatMessageOut> execute(@NonNull Context<FormatMessageIn> ctx) {
    FormatMessageIn input = ctx.body();
    if (input.template() == null) {
      return Result.failure(new IllegalArgumentException("template is required"));
    }
    Map<String, Object> params = input.params() == null ? Map.of() : input.params();
    Object evaluated = ctx.eval(input.template(), params);
    String result = evaluated == null ? "" : formatValue(evaluated);
    return Result.success(new FormatMessageOut(result));
  }

  private static String formatValue(Object value) {
    if (value instanceof java.math.BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    return String.valueOf(value);
  }
}
