package cbs.nova.starter.helpers;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helpers.model.FilterRecordsIn;
import cbs.nova.starter.helpers.model.FilterRecordsOut;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Helper(name = "filterRecords")
public class FilterRecordsHelper implements Executable<FilterRecordsIn, FilterRecordsOut> {

  @Override
  public @NonNull Result<FilterRecordsOut> execute(@NonNull Context<FilterRecordsIn> ctx) {
    FilterRecordsIn input = ctx.body();
    if (input.records() == null) {
      return Result.success(new FilterRecordsOut(List.of()));
    }
    List<Map<String, Object>> matched = input.records().stream()
            .filter(r -> r != null && Objects.equals(r.get(input.field()), input.value()))
            .toList();
    return Result.success(new FilterRecordsOut(matched));
  }
}
