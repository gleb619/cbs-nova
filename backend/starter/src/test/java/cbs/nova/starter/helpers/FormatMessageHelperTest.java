package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helpers.model.FormatMessageIn;
import cbs.nova.starter.helpers.model.FormatMessageOut;
import org.junit.jupiter.api.Test;

import java.util.Map;

class FormatMessageHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final FormatMessageHelper helper = new FormatMessageHelper();

  @Test
  void replacesPlaceholders() {
    var ctx = contextFactory.of(
            new FormatMessageIn("Hello {name}!", Map.of("name", "World")),
            ExecutionMode.PREVIEW);
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Hello World!");
  }

  @Test
  void evaluatesArithmeticExpression() {
    var ctx = contextFactory.of(
            new FormatMessageIn("Total: ${a + b}", Map.of("a", 10, "b", 20)),
            ExecutionMode.PREVIEW);
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Total: 30");
  }

  @Test
  void resolvesVariablesFromMetadata() {
    var ctx = contextFactory.of(
            new FormatMessageIn("Tenant: {tenant}", Map.of()),
            Map.<String, Object>of("tenant", "acme"),
            ExecutionMode.PREVIEW,
            contextFactory.generateRunId());
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("Tenant: acme");
  }

  @Test
  void returnsTemplateUnchangedWhenNoParams() {
    var ctx = contextFactory.of(new FormatMessageIn("No params", null),
            ExecutionMode.PREVIEW);
    Result<FormatMessageOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().result()).isEqualTo("No params");
  }

  @Test
  void failsWhenTemplateNull() {
    var ctx = contextFactory.of(new FormatMessageIn(null, null),
            ExecutionMode.PREVIEW);
    assertThat(helper.execute(ctx).isSuccess()).isFalse();
  }
}
