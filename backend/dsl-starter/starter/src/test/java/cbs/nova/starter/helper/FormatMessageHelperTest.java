package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.FormatMessageIn;
import cbs.nova.starter.helper.model.FormatMessageOut;
import org.junit.jupiter.api.Test;

import java.util.Map;

class FormatMessageHelperTest {

  private final FormatMessageHelper helper = new FormatMessageHelper();

  @Test
  void replacesPlaceholders() {
    var ctx = SimpleContext.<FormatMessageIn>builder()
            .body(new FormatMessageIn("Hello {name}!", Map.of("name", "World")))
            .mode(ExecutionMode.PREVIEW).build();
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hello World!");
  }

  @Test
  void evaluatesArithmeticExpression() {
    var ctx = SimpleContext.<FormatMessageIn>builder()
            .body(new FormatMessageIn("Total: ${a + b}", Map.of("a", 10, "b", 20)))
            .mode(ExecutionMode.PREVIEW).build();
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Total: 30");
  }

  @Test
  void resolvesVariablesFromMetadata() {
    var ctx = SimpleContext.<FormatMessageIn>builder()
            .body(new FormatMessageIn("Tenant: {tenant}", Map.of()))
            .metadata(Map.<String, Object>of("tenant", "acme")).mode(ExecutionMode.PREVIEW)
            .runId(SimpleContext.generateRunId()).build();
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Tenant: acme");
  }

  @Test
  void returnsTemplateUnchangedWhenNoParams() {
    var ctx = SimpleContext.<FormatMessageIn>builder().body(new FormatMessageIn("No params", null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("No params");
  }

  @Test
  void failsWhenTemplateNull() {
    var ctx = SimpleContext.<FormatMessageIn>builder().body(new FormatMessageIn(null, null))
            .mode(ExecutionMode.PREVIEW).build();
    assertThat(helper.execute(ctx).isSuccess()).isFalse();
  }
}
