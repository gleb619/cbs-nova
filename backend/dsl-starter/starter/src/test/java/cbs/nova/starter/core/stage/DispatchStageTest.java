package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.dsl.helper.NoopHelperInterceptor;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class DispatchStageTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    GlobalManager.globalManager().registerHelper("echo", new EchoHelper());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void threadsInterceptorThroughContextDuringDispatch() {
    AtomicBoolean intercepted = new AtomicBoolean(false);
    HelperInterceptor interceptor = (helperName, ctx) -> {
      if ("echo".equals(helperName)) {
        intercepted.set(true);
        return Optional.of(Result.success("faked"));
      }
      return Optional.empty();
    };
    var stage = DispatchStage.inline(interceptor);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.RUN, "run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    stage.execute(pipeContext, next);

    // Interceptor was consulted during dispatch and the faked result propagated.
    assertThat(intercepted.get()).isTrue();
    Result<?> dslResult = (Result<?>) pipeContext.getAttribute("dslResult");
    assertThat(dslResult).isNotNull();
    assertThat(dslResult.value()).isEqualTo("faked");
  }

  @Test
  void downstreamExceptionDoesNotLeakInterceptorState() {
    AtomicBoolean intercepted = new AtomicBoolean(false);
    HelperInterceptor interceptor = (helperName, ctx) -> {
      intercepted.set(true);
      return Optional.empty();
    };
    var stage = DispatchStage.inline(interceptor);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-3").build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.RUN, "run-3");
    DslPipeStage.Next next = c -> {
      throw new RuntimeException("downstream boom");
    };

    assertThatThrownBy(() -> stage.execute(pipeContext, next))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("downstream boom");

    // Interceptor fired during dispatch (so the real helper ran with no short-circuit).
    assertThat(intercepted.get()).isTrue();
    // No "finally" was needed: the interceptor lived on the mode context, not on a ThreadLocal.
    // A fresh, un-decorated context must therefore NOT see the interceptor.
    Context<?> followUp = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-4")
            .build();
    assertThat(followUp.helperInterceptor()).isSameAs(NoopHelperInterceptor.INSTANCE);
    Result<?> after = GlobalManager.globalManager().runHelper("echo", followUp);
    assertThat(after.value()).isEqualTo("real");
  }

  @Test
  void doesNotMutateGlobalManager() {
    // T417 regression: pre-T417 the stage called gm.registerHelperInterceptor(...) inside a
    // try/finally, which mutated the GlobalManager singleton. After T417 the interceptor lives
    // on the per-execution Context, so the singleton stays untouched.
    AtomicBoolean intercepted = new AtomicBoolean(false);
    HelperInterceptor interceptor = (helperName, ctx) -> {
      intercepted.set(true);
      return Optional.empty();
    };
    var stage = DispatchStage.inline(interceptor);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-no-mutate")
            .build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.RUN,
            "run-no-mutate");
    DslPipeStage.Next next = c -> Result.success("ok");

    stage.execute(pipeContext, next);

    assertThat(intercepted.get()).isTrue();
    // The mode context built for the run carried the interceptor; the original ctx did not.
    assertThat(ctx.helperInterceptor()).isSameAs(NoopHelperInterceptor.INSTANCE);
    Result<?> after = GlobalManager.globalManager().runHelper("echo", ctx);
    assertThat(after.value()).isEqualTo("real");
  }

  @Test
  void withNullInterceptorBehavesLikeOriginalHelper() {
    // Passing a null interceptor must NOT cause a NPE; helpers should run as before.
    var stage = DispatchStage.inline(null);

    Context<?> ctx = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-null")
            .build();
    DslPipeContext pipeContext = DslPipeContext.of("echo", ctx, ExecutionMode.RUN, "run-null");
    DslPipeStage.Next next = c -> Result.success("ok");

    stage.execute(pipeContext, next);

    Result<?> dslResult = (Result<?>) pipeContext.getAttribute("dslResult");
    assertThat(dslResult).isNotNull();
    assertThat(dslResult.isSuccess()).isTrue();
    assertThat(dslResult.value()).isEqualTo("real");
  }

  private static final class EchoHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("real");
    }
  }
}
