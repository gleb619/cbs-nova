package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.starter.helpers.model.CurrentTimestampIn;
import cbs.nova.starter.helpers.model.CurrentTimestampOut;
import org.junit.jupiter.api.Test;

class CurrentTimestampHelperTest {
  private final CurrentTimestampHelper helper = new CurrentTimestampHelper();

  @Test
  void returnsIsoTimestamp() {
    var ctx = SimpleContext.getInstance().of(new CurrentTimestampIn(null), ExecutionMode.PREVIEW);
    Result<CurrentTimestampOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().timestamp()).isNotBlank();
    assertThat(result.value().timestamp()).contains("T");
  }

  @Test
  void acceptsValidZone() {
    var ctx = SimpleContext.getInstance().of(new CurrentTimestampIn("Europe/London"),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
  }

  @Test
  void fallsBackToUtcForInvalidZone() {
    var ctx = SimpleContext.getInstance().of(new CurrentTimestampIn("Not/AZone"),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
  }
}
