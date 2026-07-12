package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

class DefaultHelperRunnerUnknownNameTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ExecutionTraceCollector traceCollector = new ExecutionTraceCollector();
  private final DefaultHelperRunner runner = new DefaultHelperRunner(traceCollector,
          contextFactory);

  @Test
  void runHelperWithUnregisteredNameReturnsEntityNotFoundFailure() {
    var registry = new DefaultHelperRegistry();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-missing-helper");

    var result = runner.runHelper("ghost", ctx, registry);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(DslEntityNotFoundException.class)
            .hasMessageContaining("Helper not found: ghost");
    assertThat(((DslEntityNotFoundException) result.cause()).runId())
            .isEqualTo(ctx.runId());
  }

  @Test
  void runFunctionWithUnregisteredNameReturnsEntityNotFoundFailure() {
    var registry = new DefaultHelperRegistry();
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-missing-function");

    var result = runner.runFunction("ghost", ctx, registry);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause())
            .isInstanceOf(DslEntityNotFoundException.class)
            .hasMessageContaining("Function not found: ghost");
    assertThat(((DslEntityNotFoundException) result.cause()).runId())
            .isEqualTo(ctx.runId());
  }

  @Test
  void runHelperWrapsExecuteExceptionAsDslExecutionExceptionWithContextRunId() {
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("boom", new ThrowingHelper());
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-throwing-helper");

    var result = runner.runHelper("boom", ctx, registry);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    var dslEx = (DslExecutionException) result.cause();
    assertThat(dslEx.runId()).isEqualTo(ctx.runId());
    assertThat(dslEx.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(dslEx.getCause().getMessage()).isEqualTo("kaboom");
    assertThat(dslEx.getMessage()).isEqualTo("kaboom");
  }

  @Test
  void runFunctionWrapsExecuteExceptionAsDslExecutionExceptionWithContextRunId() {
    var registry = new DefaultHelperRegistry();
    registry.registerFunction(
            Dsl.function("boomFn")
                    .execute(ctx -> {
                      throw new IllegalStateException("fn-kaboom");
                    })
                    .build());
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-throwing-function");

    var result = runner.runFunction("boomFn", ctx, registry);

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslExecutionException.class);
    var dslEx = (DslExecutionException) result.cause();
    assertThat(dslEx.runId()).isEqualTo(ctx.runId());
    assertThat(dslEx.getCause()).isInstanceOf(IllegalStateException.class);
    assertThat(dslEx.getCause().getMessage()).isEqualTo("fn-kaboom");
    assertThat(dslEx.getMessage()).isEqualTo("fn-kaboom");
  }

  private static final class ThrowingHelper implements Executable<String, String> {

    @Override
    public @NonNull Result<String> execute(@NonNull Context<String> ctx) {
      throw new IllegalStateException("kaboom");
    }
  }
}
