package cbs.nova.starter.metric;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CallKind;
import cbs.nova.dsl.CallNode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.history.DslRunStatus;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.stage.MetricsStage;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

class LatencyHistogramMetricsTest {

  @Test
  void previewTimerPublishesPercentileHistogramBuckets() {
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    DslPipeContext pipeContext = DslPipeContext.of(
            "Ping",
            SimpleContext.builder("body").mode(ExecutionMode.PREVIEW).runId("run-1").build(),
            ExecutionMode.PREVIEW,
            "run-1");
    pipeContext.setAttribute("astTree",
            CallNode.leaf("root", CallKind.PROCESS, null, "ok", true));

    DslPipeStage.Next next = c -> cbs.nova.dsl.Result.success("downstream");
    new MetricsStage(registry).execute(pipeContext, next);

    String scrape = registry.scrape();
    assertThat(scrape).contains("dsl_preview_duration_seconds_bucket");
    assertThat(scrape).contains("le=\"2.0\"");
    assertThat(registry.find(StarterConstants.DURATION_TIMER).timers())
            .hasSize(1);
  }

  @Test
  void runDurationTimerPublishesPercentileHistogramBucketsAndOutcomeTag() {
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    RunDurationMetricsCollector collector = new RunDurationMetricsCollector(registry);

    collector.record("Loan", DslRunStatus.COMPLETED.name(), Duration.ofSeconds(5));
    collector.record("Loan", DslRunStatus.FAILED.name(), Duration.ofSeconds(10));

    String scrape = registry.scrape();
    assertThat(scrape).contains("dsl_run_duration_seconds_bucket");
    assertThat(scrape).contains("le=\"60.0\"");
    assertThat(scrape).contains("outcome=\"ok\"");
    assertThat(scrape).contains("outcome=\"error\"");
    assertThat(scrape).contains("status=\"COMPLETED\"");
    assertThat(scrape).contains("status=\"FAILED\"");
    assertThat(scrape).contains("processName=\"Loan\"");
  }

  @Test
  void runDurationCollectorClampsNegativeDurationToZero() {
    PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
    RunDurationMetricsCollector collector = new RunDurationMetricsCollector(registry);

    collector.record("Loan", DslRunStatus.COMPLETED.name(), Duration.ofMillis(-100));

    Timer timer = registry.get(StarterConstants.RUN_DURATION_TIMER).timer();
    assertThat(timer.count()).isEqualTo(1L);
    assertThat(timer.totalTime(java.util.concurrent.TimeUnit.NANOSECONDS))
            .isEqualTo(0.0);
  }
}
