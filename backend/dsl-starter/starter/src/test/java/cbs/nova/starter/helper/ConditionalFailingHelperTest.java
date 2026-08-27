package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.ConditionalFailIn;
import cbs.nova.starter.helper.model.ConditionalFailOut;
import org.junit.jupiter.api.Test;

class ConditionalFailingHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ConditionalFailingHelper helper = new ConditionalFailingHelper();

  @Test
  void returnsSuccessWhenNotFailing() {
    var ctx = contextFactory.of(new ConditionalFailIn(false, null),
            ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().status()).isEqualTo("ok");
  }

  @Test
  void returnsFailureWhenShouldFail() {
    var ctx = contextFactory.of(new ConditionalFailIn(true, "test failure"),
            ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isEqualTo("test failure");
  }

  @Test
  void usesDefaultReasonWhenNullReason() {
    var ctx = contextFactory.of(new ConditionalFailIn(true, null),
            ExecutionMode.PREVIEW);
    Result<ConditionalFailOut> result = helper.execute(ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause().getMessage()).isNotBlank();
  }
}
