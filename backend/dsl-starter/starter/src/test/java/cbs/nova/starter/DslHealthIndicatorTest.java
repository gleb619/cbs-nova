package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.GeneratedClassDescriptor;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.DslHealthIndicator;
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
import org.springframework.boot.health.contributor.Status;

class DslHealthIndicatorTest {

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void healthIsUpWhenEmpty() {
    var indicator = new DslHealthIndicator();
    assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
  }

  @Test
  void healthDetailsReportCounts() {
    GlobalManager.globalManager()
            .registerProcess(
                    Dsl.process("Loan").execute(ctx -> Result.success("ok")).build());
    var indicator = new DslHealthIndicator();
    var details = indicator.health().getDetails();
    assertThat(details.get("processes")).isEqualTo(1);
    assertThat(details.get("transactions")).isEqualTo(0);
    assertThat(details.get("helpers")).isEqualTo(0);
  }

  @Test
  void healthOmitsTemporalDetailWhenProbeAbsent() {
    DslHealthIndicator indicator = new DslHealthIndicator(emptyProvider(), emptyProvider());

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    assertThat(health.getDetails()).doesNotContainKey("temporal");
  }

  @Test
  void healthReportsReachableTemporalWhenProbeHealthy() {
    TemporalHealthProbe probe = stubProbe(
            TemporalHealth.reachable("127.0.0.1:7233"));
    GlobalManager.globalManager().registerGeneratedClass(
            new GeneratedClassDescriptor(
                    "Loan", DslType.PROCESS, "1.0", "loan-queue",
                    Runnable.class, Runnable.class, null, String.class, "{}"));

    DslHealthIndicator indicator = new DslHealthIndicator(
            singleProvider(probe), emptyProvider());

    var health = indicator.health();

    assertThat(health.getStatus()).isEqualTo(Status.UP);
    @SuppressWarnings("unchecked")
    Map<String, Object> temporal = (Map<String, Object>) health.getDetails().get("temporal");
    assertThat(temporal).isNotNull();
    assertThat(temporal.get("reachable")).isEqualTo(true);
    assertThat(temporal.get("target")).isEqualTo("127.0.0.1:7233");
    assertThat(temporal).doesNotContainKey("error");
    assertThat((List<String>) temporal.get("configuredTaskQueues"))
            .contains("loan-queue");
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
    @SuppressWarnings("unchecked")
    Map<String, Object> temporal = (Map<String, Object>) health.getDetails().get("temporal");
    assertThat(temporal.get("reachable")).isEqualTo(false);
    assertThat(temporal.get("error")).isEqualTo("connection refused");
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
    @SuppressWarnings("unchecked")
    Map<String, Object> temporal = (Map<String, Object>) health.getDetails().get("temporal");
    assertThat(temporal.get("reachable")).isEqualTo(false);
    assertThat(temporal.get("error")).isEqualTo("connection refused");
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
}
