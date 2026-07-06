package cbs.nova.starter.helpers;

import static org.assertj.core.api.Assertions.*;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.SimpleContext;
import cbs.nova.starter.helpers.model.ConditionalFailIn;
import cbs.nova.starter.helpers.model.ConditionalFailOut;
import org.junit.jupiter.api.Test;

class ConditionalFailingHelperTest {
  private final ConditionalFailingHelper helper = new ConditionalFailingHelper();

  @Test
  void returnsSuccessWhenNotFailing() {
    var ctx = SimpleContext.of(new ConditionalFailIn(false, null), ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().status()).isEqualTo("ok");
  }

  @Test
  void returnsFailureWhenShouldFail() {
    var ctx = SimpleContext.of(new ConditionalFailIn(true, "test failure"), ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isEqualTo("test failure");
  }

  @Test
  void usesDefaultReasonWhenNullReason() {
    var ctx = SimpleContext.of(new ConditionalFailIn(true, null), ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isNotBlank();
  }
}
