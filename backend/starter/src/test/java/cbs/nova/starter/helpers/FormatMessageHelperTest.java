package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FormatMessageHelperTest {
  private final FormatMessageHelper helper = new FormatMessageHelper();

  @Test
  void replacesPlaceholders() {
    var ctx = SimpleContext.getInstance().of(
            new FormatMessageIn("Hello {name}!", Map.of("name", "World")),
            ExecutionMode.PREVIEW);
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hello World!");
  }

  @Test
  void returnsTemplateUnchangedWhenNoParams() {
    var ctx = SimpleContext.getInstance().of(new FormatMessageIn("No params", null),
            ExecutionMode.PREVIEW);
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("No params");
  }

  @Test
  void failsWhenTemplateNull() {
    var ctx = SimpleContext.getInstance().of(new FormatMessageIn(null, null),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isFalse();
  }
}
