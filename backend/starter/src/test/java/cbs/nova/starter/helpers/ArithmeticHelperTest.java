package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.SumValuesIn;
import cbs.nova.starter.helpers.model.SumValuesOut;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

class ArithmeticHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ArithmeticHelper helper = new ArithmeticHelper();

  @Test
  void addsValues() {
    var ctx = contextFactory.of(new SumValuesIn(List.of(1.0, 2.0, 3.0)),
            ExecutionMode.PREVIEW);
    Result<SumValuesOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().sum()).isEqualByComparingTo(BigDecimal.valueOf(6));
  }

  @Test
  void subtractsValues() {
    var ctx = contextFactory.of(
            new SumValuesIn(List.of(10, 3, 2), SumValuesIn.Operation.SUBTRACT.name()),
            ExecutionMode.PREVIEW);
    Result<SumValuesOut> result = helper.execute(ctx);
    assertThat(result.value().sum()).isEqualByComparingTo(BigDecimal.valueOf(5));
  }

  @Test
  void multipliesValues() {
    var ctx = contextFactory.of(
            new SumValuesIn(List.of(2, 3, 4), SumValuesIn.Operation.MULTIPLY.name()),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.valueOf(24));
  }

  @Test
  void dividesValues() {
    var ctx = contextFactory.of(
            new SumValuesIn(List.of(100, 4, 5), SumValuesIn.Operation.DIVIDE.name()),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.valueOf(5));
  }

  @Test
  void computesMinAndMax() {
    var min = helper.execute(contextFactory.of(
            new SumValuesIn(List.of(3, 1, 2), SumValuesIn.Operation.MIN.name()),
            ExecutionMode.PREVIEW));
    assertThat(min.value().sum()).isEqualByComparingTo(BigDecimal.ONE);

    var max = helper.execute(contextFactory.of(
            new SumValuesIn(List.of(3, 1, 2), SumValuesIn.Operation.MAX.name()),
            ExecutionMode.PREVIEW));
    assertThat(max.value().sum()).isEqualByComparingTo(BigDecimal.valueOf(3));
  }

  @Test
  void returnsZeroForEmpty() {
    var ctx = contextFactory.of(new SumValuesIn(List.of()), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void returnsZeroForNullList() {
    var ctx = contextFactory.of(new SumValuesIn(null), ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).value().sum()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
