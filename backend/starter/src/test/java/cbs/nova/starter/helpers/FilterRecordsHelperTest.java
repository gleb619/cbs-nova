package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.FilterRecordsIn;
import cbs.nova.starter.helpers.model.FilterRecordsOut;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class FilterRecordsHelperTest {
  private final ContextFactory contextFactory = new ContextFactory();
  private final FilterRecordsHelper helper = new FilterRecordsHelper();

  @Test
  void filtersMatchingRecords() {
    var records = List.of(
            Map.<String, Object>of("status", "active", "name", "a"),
            Map.<String, Object>of("status", "inactive", "name", "b"),
            Map.<String, Object>of("status", "active", "name", "c"));
    var ctx = contextFactory.of(new FilterRecordsIn(records, "status", "active"),
            ExecutionMode.PREVIEW);
    Result<FilterRecordsOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().matched()).hasSize(2);
  }

  @Test
  void returnsEmptyForNullRecords() {
    var ctx = contextFactory.of(new FilterRecordsIn(null, "field", "val"),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().matched()).isEmpty();
  }
}
