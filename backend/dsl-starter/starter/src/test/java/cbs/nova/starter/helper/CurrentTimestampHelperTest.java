package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.CurrentTimestampIn;
import cbs.nova.starter.helper.model.CurrentTimestampOut;
import org.junit.jupiter.api.Test;

class CurrentTimestampHelperTest {

  private final CurrentTimestampHelper helper = new CurrentTimestampHelper();

  @Test
  void returnsIsoTimestamp() {
    var ctx = SimpleContext.<CurrentTimestampIn>builder().body(new CurrentTimestampIn(null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<CurrentTimestampOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().timestamp()).isNotBlank();
    assertThat(result.value().timestamp()).contains("T");
  }

  @Test
  void acceptsValidZone() {
    var ctx = SimpleContext.<CurrentTimestampIn>builder()
            .body(new CurrentTimestampIn("Europe/London")).mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
  }

  @Test
  void fallsBackToUtcForInvalidZone() {
    var ctx = SimpleContext.<CurrentTimestampIn>builder().body(new CurrentTimestampIn("Not/AZone"))
            .mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).isSuccess()).isTrue();
  }
}
