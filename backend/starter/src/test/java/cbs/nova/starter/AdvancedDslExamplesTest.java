package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.HelperInstanceResolver;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dslexamples.ExceptionProbeModels.ExceptionProbeIn;
import cbs.nova.dslexamples.ExceptionProbeModels.ExceptionProbeOut;
import cbs.nova.dslexamples.NestedCompensationModels.NestedCompensationIn;
import cbs.nova.dslexamples.OrderSagaModels.OrderSagaIn;
import cbs.nova.dslexamples.OrderSagaModels.OrderSagaOut;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

class AdvancedDslExamplesTest {

  private final ContextFactory contextFactory = new ContextFactory();
  @TempDir
  Path dslSourceDir;

  @BeforeEach
  void loadCompactDsls() throws Exception {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(reflectiveHelperResolver());
    copyCompactDsl("OrderSagaDsl.java");
    copyCompactDsl("ExceptionProbeDsl.java");
    copyCompactDsl("NestedCompensationDsl.java");

    new DefinitionLoader().load(dslSourceDir, GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void orderSagaPreviewCompletesSuccessfully() {
    var input = new OrderSagaIn("order1", 2);
    Context<OrderSagaIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("OrderSaga", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(OrderSagaOut.class);
  }

  @Test
  void exceptionProbePreviewSucceedsWhenHelperSucceeds() {
    var input = new ExceptionProbeIn(false, null);
    Context<ExceptionProbeIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isTrue();
    ExceptionProbeOut out = (ExceptionProbeOut) result.value();
    assertThat(out.result()).isEqualTo("SUCCESS");
  }

  @Test
  void exceptionProbePreviewFailsWhenHelperFails() {
    var input = new ExceptionProbeIn(true, "test fail");
    Context<ExceptionProbeIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void nestedCompensationPreviewFailsAtStep3() {
    var input = new NestedCompensationIn("job1");
    Context<NestedCompensationIn> ctx = contextFactory.of(input,
            ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.globalManager().runProcess("NestedCompensation", ctx);

    assertThat(result.isSuccess()).isFalse();
  }

  private void copyCompactDsl(String name) throws Exception {
    try (InputStream in = getClass().getResourceAsStream("/dsl-advanced-examples/" + name)) {
      if (in == null) {
        throw new IllegalStateException("Missing test resource: " + name);
      }
      Files.copy(in, dslSourceDir.resolve(name));
    }
  }

  private static HelperInstanceResolver reflectiveHelperResolver() {
    return helperClass -> {
      try {
        //TODO: remove reflection, use typed info instead
        return (Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass, e);
      }
    };
  }
}
