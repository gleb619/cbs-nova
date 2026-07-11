package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.SumValuesIn;
import cbs.nova.starter.helpers.model.SumValuesOut;
import org.junit.jupiter.api.Test;

import java.util.List;

class SumValuesHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final SumValuesHelper helper = new SumValuesHelper();

  @Test
  void sumsValues() {
    var ctx = contextFactory.of(new SumValuesIn(List.of(1.0, 2.0, 3.0)),
            ExecutionMode.PREVIEW);
    Result<SumValuesOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().sum()).isEqualTo(6.0);
  }

  @Test
  void returnsZeroForEmpty() {
    var ctx = contextFactory.of(new SumValuesIn(List.of()), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualTo(0.0);
  }

  @Test
  void returnsZeroForNullList() {
    var ctx = contextFactory.of(new SumValuesIn(null), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualTo(0.0);
  }
}
