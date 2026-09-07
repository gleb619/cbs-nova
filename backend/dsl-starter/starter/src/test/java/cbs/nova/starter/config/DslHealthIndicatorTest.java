package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.properties.CbsHealthProperties;
import cbs.nova.starter.config.properties.CbsHealthProperties.FailStatus;
import cbs.nova.starter.config.properties.CbsHealthProperties.Temporal;
import cbs.nova.starter.service.TemporalHealthProbe;
import cbs.nova.starter.service.TemporalHealthProbe.TemporalHealth;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Verifies the DSL health indicators without booting a full Spring Boot application.
 *
 * <p>Both {@link DslHealthIndicator} (plain {@code /actuator/health}) and
 * {@link DslReadinessIndicator} (readiness group) share the same contribution logic, registered
 * together by {@link DslHealthIndicatorConfiguration}. These tests pin that the generic
 * {@code dsl} component keeps its original detail keys and that the readiness indicator behaves
 * correctly when Temporal is unreachable.
 */
class DslHealthIndicatorTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(DslHealthIndicatorConfiguration.class);

  @BeforeEach
  void resetGlobalManager() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void cleanupGlobalManager() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void configurationGatedByConditionalOnClassHealthIndicator() {
    var annotation = DslHealthIndicatorConfiguration.class.getAnnotation(ConditionalOnClass.class);
    assertThat(annotation)
            .as("DslHealthIndicatorConfiguration must declare @ConditionalOnClass so it only "
                    + "activates when spring-boot-actuator's HealthIndicator is on the classpath")
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
  void dslAndReadinessIndicatorsAreBothRegistered() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(DslHealthIndicator.class);
      assertThat(ctx).hasSingleBean(DslReadinessIndicator.class);
    });
  }

  @Test
  void dslAndReadinessIndicatorsProduceSameDetails() {
    GlobalManager.globalManager()
            .registerProcess(Dsl.process("Loan").execute(ctx -> Result.success("ok")).build());

    var dsl = new DslHealthIndicator(emptyProvider(), emptyProvider());
    var readiness = new DslReadinessIndicator(emptyProvider(), emptyProvider());

    assertThat(dsl.health().getDetails()).isEqualTo(readiness.health().getDetails());
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

  @Test
  void healthOmitsTemporalDetailWhenProbeAbsent() {
    DslHealthIndicator indicator = new DslHealthIndicator(emptyProvider(), emptyProvider());

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).doesNotContainKey("temporal");
  }

  @Test
  void readinessOmitsTemporalDetailWhenProbeAbsent() {
    DslReadinessIndicator indicator = new DslReadinessIndicator(emptyProvider(), emptyProvider());

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).doesNotContainKey("temporal");
  }

  @Test
  void healthReportsReachableTemporalWhenProbeHealthy() {
    TemporalHealthProbe probe = stubProbe(TemporalHealth.reachable("127.0.0.1:7233"));
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    "Loan", DslType.PROCESS, "1.0", "loan-queue",
                    Runnable.class, Runnable.class, null, String.class, "{}"));

    DslHealthIndicator indicator = new DslHealthIndicator(
            singleProvider(probe), emptyProvider());

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertTemporalDetail(health.getDetails(), true, "127.0.0.1:7233", null, null);
    @SuppressWarnings("unchecked")
    List<String> taskQueues = (List<String>) ((Map<String, Object>) health.getDetails()
            .get("temporal")).get("configuredTaskQueues");
    assertThat(taskQueues).contains("loan-queue");
  }

  @Test
  void readinessReportsReachableTemporalWhenProbeHealthy() {
    TemporalHealthProbe probe = stubProbe(TemporalHealth.reachable("127.0.0.1:7233"));

    DslReadinessIndicator indicator = new DslReadinessIndicator(
            singleProvider(probe), emptyProvider());

    var health = indicator.health();
    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertTemporalDetail(health.getDetails(), true, "127.0.0.1:7233", null,
            null);
  }

  @Test
  void healthStaysUpWhenTemporalUnreachableAndFailStatusIsNone() {
    TemporalHealthProbe probe = stubProbe(
            TemporalHealth.unreachable("127.0.0.1:7233", "connection refused"));
    CbsHealthProperties props = new CbsHealthProperties(
            new Temporal(FailStatus.NONE, Duration.ofSeconds(2)));

    DslHealthIndicator indicator = new DslHealthIndicator(
            singleProvider(probe), singleProvider(props));

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertTemporalDetail(health.getDetails(), false, "127.0.0.1:7233", "connection refused",
            null);
  }

  @Test
  void readinessStaysUpWhenTemporalUnreachableAndFailStatusIsNone() {
    TemporalHealthProbe probe = stubProbe(
            TemporalHealth.unreachable("127.0.0.1:7233", "connection refused"));
    CbsHealthProperties props = new CbsHealthProperties(
            new Temporal(FailStatus.NONE, Duration.ofSeconds(2)));

    DslReadinessIndicator indicator = new DslReadinessIndicator(
            singleProvider(probe), singleProvider(props));

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertTemporalDetail(health.getDetails(), false, "127.0.0.1:7233", "connection refused",
            null);
  }

  @Test
  void healthReportsDownWhenTemporalUnreachableAndFailStatusIsDown() {
    TemporalHealthProbe probe = stubProbe(
            TemporalHealth.unreachable("127.0.0.1:7233", "connection refused"));
    CbsHealthProperties props = new CbsHealthProperties(
            new Temporal(FailStatus.DOWN, Duration.ofSeconds(2)));

    DslHealthIndicator indicator = new DslHealthIndicator(
            singleProvider(probe), singleProvider(props));

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertTemporalDetail(health.getDetails(), false, "127.0.0.1:7233", "connection refused",
            null);
  }

  @Test
  void readinessReportsDownWhenTemporalUnreachableAndFailStatusIsDown() {
    TemporalHealthProbe probe = stubProbe(
            TemporalHealth.unreachable("127.0.0.1:7233", "connection refused"));
    CbsHealthProperties props = new CbsHealthProperties(
            new Temporal(FailStatus.DOWN, Duration.ofSeconds(2)));

    DslReadinessIndicator indicator = new DslReadinessIndicator(
            singleProvider(probe), singleProvider(props));

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.DOWN);
    assertTemporalDetail(health.getDetails(), false, "127.0.0.1:7233", "connection refused",
            null);
  }

  private static void assertTemporalDetail(Map<String, Object> details,
          boolean reachable, String target, String error, List<String> taskQueues) {
    @SuppressWarnings("unchecked")
    Map<String, Object> temporal = (Map<String, Object>) details.get("temporal");
    assertThat(temporal).isNotNull();
    assertThat(temporal.get("reachable")).isEqualTo(reachable);
    assertThat(temporal.get("target")).isEqualTo(target);
    if (taskQueues != null) {
      assertThat((List<String>) temporal.get("configuredTaskQueues")).isEqualTo(taskQueues);
    }
    if (error == null) {
      assertThat(temporal).doesNotContainKey("error");
    } else {
      assertThat(temporal.get("error")).isEqualTo(error);
    }
  }

  private static TemporalHealthProbe stubProbe(TemporalHealth canned) {
    TemporalHealthProbe probe = mock(TemporalHealthProbe.class);
    when(probe.probe()).thenReturn(canned);
    return probe;
  }

  private static <T> ObjectProvider<T> singleProvider(T instance) {
    @SuppressWarnings("unchecked")
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(instance);
    return provider;
  }

  @SuppressWarnings("unchecked")
  private static <T> ObjectProvider<T> emptyProvider() {
    ObjectProvider<T> provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable()).thenReturn(null);
    return provider;
  }

  private static final class EchoHelper implements Executable<Object, Object> {
    @Override
    public Result<Object> execute(Context<Object> ctx) {
      return Result.success("ok");
    }
  }
}
