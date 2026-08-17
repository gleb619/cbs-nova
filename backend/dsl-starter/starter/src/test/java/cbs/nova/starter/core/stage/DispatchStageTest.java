package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.helper.HelperInterceptor;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

class DispatchStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

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
  void registersInterceptorPerRunAndClearsInFinally() {
    AtomicBoolean intercepted = new AtomicBoolean(false);
    HelperInterceptor interceptor = (helperName, ctx) -> {
      if ("echo".equals(helperName)) {
        intercepted.set(true);
        return Optional.of(Result.success("faked"));
      }
      return Optional.empty();
    };
    var stage = new DispatchStage(contextFactory, interceptor);

    Context<?> ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    DslPipeContext pipeContext = new DslPipeContext("echo", ctx, ExecutionMode.RUN, "run-1");
    DslPipeStage.Next next = c -> Result.success("downstream");

    stage.execute(pipeContext, next);

    // Interceptor was consulted during dispatch and the faked result propagated.
    assertThat(intercepted.get()).isTrue();
    Result<?> dslResult = (Result<?>) pipeContext.getAttribute("dslResult");
    assertThat(dslResult).isNotNull();
    assertThat(dslResult.value()).isEqualTo("faked");

    // Finally cleared the interceptor: a direct helper call no longer short-circuits.
    Context<?> followUp = contextFactory.of("body", ExecutionMode.RUN, "run-2");
    Result<?> after = GlobalManager.globalManager().runHelper("echo", followUp);
    assertThat(after.value()).isEqualTo("real");
  }

  @Test
  void clearsInterceptorEvenWhenDownstreamThrows() {
    AtomicBoolean intercepted = new AtomicBoolean(false);
    HelperInterceptor interceptor = (helperName, ctx) -> {
      intercepted.set(true);
      return Optional.empty();
    };
    var stage = new DispatchStage(contextFactory, interceptor);

    Context<?> ctx = contextFactory.of("body", ExecutionMode.RUN, "run-3");
    DslPipeContext pipeContext = new DslPipeContext("echo", ctx, ExecutionMode.RUN, "run-3");
    DslPipeStage.Next next = c -> {
      throw new RuntimeException("downstream boom");
    };

    assertThatThrownBy(() -> stage.execute(pipeContext, next))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("downstream boom");

    // Interceptor was registered during dispatch and the real helper ran (no short-circuit).
    assertThat(intercepted.get()).isTrue();
    // Finally cleared the interceptor even though the downstream stage threw.
    Context<?> followUp = contextFactory.of("body", ExecutionMode.RUN, "run-4");
    Result<?> after = GlobalManager.globalManager().runHelper("echo", followUp);
    assertThat(after.value()).isEqualTo("real");
  }

  /** Simple helper returning a constant so we can tell faked vs real results apart. */
  private static final class EchoHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("real");
    }
  }
}
