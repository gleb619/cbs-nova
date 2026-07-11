package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dslmodel.ExceptionProbeIn;
import cbs.nova.dslmodel.ExceptionProbeOut;
import cbs.nova.dslmodel.NestedCompensationIn;
import cbs.nova.dslmodel.OrderSagaIn;
import cbs.nova.dslmodel.OrderSagaOut;
import cbs.nova.starter.config.DslAutoConfiguration;
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
    GlobalManager.getInstance().resetForTests();
    copyCompactDsl("OrderSagaDsl.java");
    copyCompactDsl("ExceptionProbeDsl.java");
    copyCompactDsl("NestedCompensationDsl.java");

    var config = new DslAutoConfiguration();
    setField(config, "sourceDirProperty", dslSourceDir.toString());
    setField(config, "helperScanPackages", "cbs.nova.starter.helpers");
    config.loadDslDefinitions();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void orderSagaPreviewCompletesSuccessfully() {
    var input = new OrderSagaIn("order1", 2);
    Context<OrderSagaIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.getInstance().runProcess("OrderSaga", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isInstanceOf(OrderSagaOut.class);
  }

  @Test
  void exceptionProbePreviewSucceedsWhenHelperSucceeds() {
    var input = new ExceptionProbeIn(false, null);
    Context<ExceptionProbeIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.getInstance().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isTrue();
    ExceptionProbeOut out = (ExceptionProbeOut) result.value();
    assertThat(out.result()).isEqualTo("SUCCESS");
  }

  @Test
  void exceptionProbePreviewFailsWhenHelperFails() {
    var input = new ExceptionProbeIn(true, "test fail");
    Context<ExceptionProbeIn> ctx = contextFactory.of(input, ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.getInstance().runProcess("ExceptionProbe", ctx);

    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void nestedCompensationPreviewFailsAtStep3() {
    var input = new NestedCompensationIn("job1");
    Context<NestedCompensationIn> ctx = contextFactory.of(input,
            ExecutionMode.PREVIEW);

    Result<?> result = GlobalManager.getInstance().runProcess("NestedCompensation", ctx);

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

  private void setField(Object target, String fieldName, String value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
