package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.SumValuesIn;
import cbs.nova.starter.helpers.model.SumValuesOut;
import org.jspecify.annotations.NonNull;

@Helper(name = "sumValues")
public class SumValuesHelper implements Executable<SumValuesIn, SumValuesOut> {

  @Override
  public @NonNull Result<SumValuesOut> execute(@NonNull Context<SumValuesIn> ctx) {
    SumValuesIn input = ctx.body();
    if (input.values() == null || input.values().isEmpty()) {
      return Result.success(new SumValuesOut(0.0));
    }
    double sum = input.values().stream().mapToDouble(Double::doubleValue).sum();
    return Result.success(new SumValuesOut(sum));
  }
}
