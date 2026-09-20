package cbs.nova.starter.core.stage;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.Constants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class CurrentObjectNameStageTest {

  @Test
  void stampsCurrentObjectNameIntoMetadata() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.RUN).runId("run-1")
            .build();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.RUN, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    DslPipeStage.Next next = c -> {
      captured.set(c);
      return Result.success("downstream");
    };

    Result<?> result = new CurrentObjectNameStage().execute(pipeContext, next);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("downstream");
    DslPipeContext wrapped = captured.get();
    assertThat(wrapped).isNotSameAs(pipeContext);
    assertThat(wrapped.dslContext()).isNotSameAs(originalDsl);
    Object objectName = wrapped.dslContext().metadata(Constants.CURRENT_OBJECT_NAME);
    assertThat(objectName).isEqualTo("Ping");
  }

  @Test
  void doesNotMutateOriginalContext() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.PREVIEW)
            .runId("run-1").build();
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.PREVIEW, "run-1");

    new CurrentObjectNameStage().execute(pipeContext, c -> Result.success("downstream"));

    assertThat(originalDsl.metadata()).doesNotContainKey(Constants.CURRENT_OBJECT_NAME);
    assertThat(pipeContext.dslContext().metadata())
            .doesNotContainKey(Constants.CURRENT_OBJECT_NAME);
  }

  @Test
  void overwritesPreviouslySetObjectName() {
    Context<?> originalDsl = SimpleContext.builder("body").mode(ExecutionMode.RUN)
            .runId("run-1").build()
            .withMetadata(Constants.CURRENT_OBJECT_NAME, "OldObject");
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping", originalDsl, ExecutionMode.RUN, "run-1");

    AtomicReference<DslPipeContext> captured = new AtomicReference<>();
    new CurrentObjectNameStage().execute(pipeContext, c -> {
      captured.set(c);
      return Result.success("downstream");
    });

    Object objectName = captured.get().dslContext().metadata(Constants.CURRENT_OBJECT_NAME);
    assertThat(objectName).isEqualTo("Ping");
  }
}
