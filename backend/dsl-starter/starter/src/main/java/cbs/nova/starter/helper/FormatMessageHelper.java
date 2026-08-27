package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.FormatMessageIn;
import cbs.nova.starter.helper.model.FormatMessageOut;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
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
    if (value instanceof BigDecimal bd) {
      return bd.stripTrailingZeros().toPlainString();
    }
    return String.valueOf(value);
  }
}
