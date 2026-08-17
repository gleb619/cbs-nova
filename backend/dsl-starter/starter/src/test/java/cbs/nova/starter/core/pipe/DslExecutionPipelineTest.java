package cbs.nova.starter.core.pipe;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.Test;

class DslExecutionPipelineTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @Test
  void pipelineExecutesStagesInOrder() {
    StringBuilder order = new StringBuilder();
    DslExecutionPipeline<String> pipe = DslExecutionPipeline.<String>builder()
            .stage((ctx, next) -> {
              order.append("A");
              return next.proceed(ctx);
            })
            .stage((ctx, next) -> {
              ctx.setAttribute("dslResult", Result.success("done"));
              order.append("B");
              return next.proceed(ctx);
            })
            .stage((ctx, next) -> {
              order.append("C");
              return next.proceed(ctx);
            })
            .build();

    Context<?> ctx = contextFactory.of("in", ExecutionMode.RUN);
    Result<String> result = pipe.execute("Test", ctx);

    assertThat(order.toString()).isEqualTo("ABC");
    assertThat(result.value()).isEqualTo("done");
  }
}
