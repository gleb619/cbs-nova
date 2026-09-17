package cbs.nova.starter.core.pipe;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.Test;

class DslExecutionPipelineTest {

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

    Context<?> ctx = SimpleContext.builder("in").mode(ExecutionMode.RUN).build();
    Result<String> result = pipe.execute("Test", ctx);

    assertThat(order.toString()).isEqualTo("ABC");
    assertThat(result.value()).isEqualTo("done");
  }
}
