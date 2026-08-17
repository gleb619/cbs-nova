package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helpers.model.SumValuesIn;
import cbs.nova.starter.helpers.model.SumValuesIn.Operation;
import cbs.nova.starter.helpers.model.SumValuesOut;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;

@Helper(name = "arithmetic")
public class ArithmeticHelper implements Executable<SumValuesIn, SumValuesOut> {

  @Override
  public @NonNull Result<SumValuesOut> execute(@NonNull Context<SumValuesIn> ctx) {
    SumValuesIn input = ctx.body();
    List<Number> values = input.values();
    Operation operation = input.effectiveOperation();

    if (values == null || values.isEmpty()) {
      return Result.success(new SumValuesOut(BigDecimal.ZERO));
    }

    BigDecimal result = switch (operation) {
      case ADD -> values.stream()
              .map(ArithmeticHelper::toBigDecimal)
              .reduce(BigDecimal.ZERO, BigDecimal::add);
      case SUBTRACT -> subtract(values);
      case MULTIPLY -> values.stream()
              .map(ArithmeticHelper::toBigDecimal)
              .reduce(BigDecimal.ONE, BigDecimal::multiply);
      case DIVIDE -> divide(values);
      case MIN -> values.stream()
              .map(ArithmeticHelper::toBigDecimal)
              .reduce(BigDecimal::min)
              .orElse(BigDecimal.ZERO);
      case MAX -> values.stream()
              .map(ArithmeticHelper::toBigDecimal)
              .reduce(BigDecimal::max)
              .orElse(BigDecimal.ZERO);
    };

    return Result.success(new SumValuesOut(result));
  }

  private static BigDecimal subtract(List<Number> values) {
    BigDecimal first = toBigDecimal(values.get(0));
    return values.stream()
            .skip(1)
            .map(ArithmeticHelper::toBigDecimal)
            .reduce(first, BigDecimal::subtract);
  }

  private static BigDecimal divide(List<Number> values) {
    BigDecimal first = toBigDecimal(values.get(0));
    return values.stream()
            .skip(1)
            .map(ArithmeticHelper::toBigDecimal)
            .reduce(first, (a, b) -> a.divide(b, MathContext.DECIMAL64));
  }

  private static BigDecimal toBigDecimal(Number value) {
    if (value instanceof BigDecimal bd) {
      return bd;
    }
    return BigDecimal.valueOf(value.doubleValue());
  }
}
