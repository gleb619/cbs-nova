package cbs.nova.starter.helper;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.helper.model.ConditionalFailIn;
import cbs.nova.starter.helper.model.ConditionalFailOut;
import org.junit.jupiter.api.Test;

class ConditionalFailingHelperTest {

  private final ConditionalFailingHelper helper = new ConditionalFailingHelper();

  @Test
  void returnsSuccessWhenNotFailing() {
    var ctx = SimpleContext.<ConditionalFailIn>builder().body(new ConditionalFailIn(false, null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().status()).isEqualTo("ok");
  }

  @Test
  void returnsFailureWhenShouldFail() {
    var ctx = SimpleContext.<ConditionalFailIn>builder()
            .body(new ConditionalFailIn(true, "test failure")).mode(ExecutionMode.PREVIEW).build();
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isEqualTo("test failure");
  }

  @Test
  void usesDefaultReasonWhenNullReason() {
    var ctx = SimpleContext.<ConditionalFailIn>builder().body(new ConditionalFailIn(true, null))
            .mode(ExecutionMode.PREVIEW).build();
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isNotBlank();
  }
}
