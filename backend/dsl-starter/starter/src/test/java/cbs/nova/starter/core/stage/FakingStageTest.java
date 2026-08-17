package cbs.nova.starter.core.stage;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.fake.FakeConfig;
import cbs.nova.dsl.fake.FakeEntry;
import cbs.nova.starter.config.CbsNovaFakesProperties;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.pipe.RunScopedFakeConfig;
import org.junit.jupiter.api.Test;

class FakingStageTest {

  private final ContextFactory contextFactory = new ContextFactory();

  private DslPipeContext newContext(String runId) {
    Context<?> ctx = contextFactory.of("body", ExecutionMode.PREVIEW, runId);
    return new DslPipeContext("Ping", ctx, ExecutionMode.PREVIEW, runId);
  }

  @Test
  void registersConfigByRunIdDuringProceedAndRemovesInFinally() {
    var registry = new RunScopedFakeConfig();
    var config = FakeConfig.of(new FakeEntry("helper", "httpCall", "fake"));
    var stage = new FakingStage(new CbsNovaFakesProperties(true, config), registry);
    var pipeContext = newContext("run-1");

    boolean[] seenDuringProceed = {false};
    DslPipeStage.Next next = c -> {
      seenDuringProceed[0] = registry.find("run-1") == config;
      return Result.success("ok");
    };

    stage.execute(pipeContext, next);

    assertThat(seenDuringProceed[0]).isTrue();
    assertThat(registry.find("run-1")).isNull();
  }

  @Test
  void disabledDoesNotRegisterConfig() {
    var registry = new RunScopedFakeConfig();
    var config = FakeConfig.of(new FakeEntry("helper", "httpCall", "fake"));
    var stage = new FakingStage(new CbsNovaFakesProperties(false, config), registry);
    var pipeContext = newContext("run-2");

    boolean[] seenDuringProceed = {false};
    DslPipeStage.Next next = c -> {
      seenDuringProceed[0] = registry.find("run-2") != null;
      return Result.success("ok");
    };

    stage.execute(pipeContext, next);

    assertThat(seenDuringProceed[0]).isFalse();
    assertThat(registry.find("run-2")).isNull();
  }

  @Test
  void removesConfigEvenWhenProceedThrows() {
    var registry = new RunScopedFakeConfig();
    var config = FakeConfig.of(new FakeEntry("helper", "httpCall", "fake"));
    var stage = new FakingStage(new CbsNovaFakesProperties(true, config), registry);
    var pipeContext = newContext("run-3");

    DslPipeStage.Next next = c -> {
      throw new RuntimeException("boom");
    };

    try {
      stage.execute(pipeContext, next);
    } catch (RuntimeException expected) {
      // expected
    }

    assertThat(registry.find("run-3")).isNull();
  }
}
