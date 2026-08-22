package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies {@link DslHealthIndicator} without booting a full Spring Boot application.
 *
 * <p>
 * Unlike the other configs in this package, {@link DslHealthIndicator} has no companion
 * {@code @AutoConfiguration} class — it is registered as a {@code @Component} gated by
 * {@link ConditionalOnClass @ConditionalOnClass(HealthIndicator.class)}. There is therefore no
 * dedicated auto-configuration wiring to test; instead these tests pin both the gating annotation
 * and the runtime contract (reported {@link Status} and process/transaction/helper counts) by
 * loading the indicator into an {@link ApplicationContextRunner}.
 */
class DslHealthIndicatorTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslHealthIndicator.class);

  @BeforeEach
  void resetGlobalManager() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void cleanupGlobalManager() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void indicatorGatedByConditionalOnClassHealthIndicator() {
    var annotation = DslHealthIndicator.class.getAnnotation(ConditionalOnClass.class);
    assertThat(annotation)
            .as("DslHealthIndicator must declare @ConditionalOnClass so it only activates when "
                    + "spring-boot-actuator's HealthIndicator is on the classpath")
            .isNotNull();
    assertThat(annotation.value())
            .as("gating class must be HealthIndicator")
            .containsExactly(HealthIndicator.class);
  }

  @Test
  void emptyGlobalManagerReportsUpWithZeroCounts() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(DslHealthIndicator.class);
      DslHealthIndicator indicator = ctx.getBean(DslHealthIndicator.class);

      var health = indicator.health();
      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
              .containsEntry("processes", 0)
              .containsEntry("transactions", 0)
              .containsEntry("helpers", 0);
    });
  }

  @Test
  void detailsReflectRegisteredProcessCount() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Loan").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Payment").execute(ctx -> Result.success("ok")).build());

    runner.run(ctx -> {
      DslHealthIndicator indicator = ctx.getBean(DslHealthIndicator.class);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
              .containsEntry("processes", 2)
              .containsEntry("transactions", 0)
              .containsEntry("helpers", 0);
    });
  }

  @Test
  void detailsReflectRegisteredTransactionAndHelperCounts() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Loan").execute(ctx -> Result.success("ok")).build());
    GlobalManager.globalManager()
            .registerTransaction(Dsl.transaction("WireTransfer")
                    .execute(ctx -> Result.success("ok"))
                    .build());
    GlobalManager.globalManager().registerHelper("echo", new EchoHelper());

    runner.run(ctx -> {
      DslHealthIndicator indicator = ctx.getBean(DslHealthIndicator.class);
      var health = indicator.health();

      assertThat(health.getStatus()).isEqualTo(Status.UP);
      assertThat(health.getDetails())
              .containsEntry("processes", 1)
              .containsEntry("transactions", 1)
              .containsEntry("helpers", 1);
    });
  }

  private static final class EchoHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }
}
