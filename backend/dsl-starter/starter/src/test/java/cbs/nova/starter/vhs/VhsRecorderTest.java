package cbs.nova.starter.vhs;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.VhsConfiguration;
import cbs.nova.starter.core.event.DslExecutionEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunCompletedEvent;
import cbs.nova.starter.core.event.DslExecutionEvent.DslRunStartedEvent;
import cbs.nova.starter.core.listener.DslExecutionEventBus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class VhsRecorderTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(VhsTestConfig.class);

  @Test
  void translatesRunLifecycleAndExternalCallToTapeEvents() {
    InMemorySink sink = new InMemorySink();
    VhsRecorder recorder = new VhsRecorder(sink, List.of("Ping"));
    DslExecutionEventBus bus = new DslExecutionEventBus();
    bus.register(recorder);

    String runId = "run-1";
    String correlationId = "corr-abc";
    bus.publish(new DslRunStartedEvent(runId, "Ping", ExecutionMode.RUN, correlationId));
    bus.publish(new DslExternalCallEvent(runId, "helper", "FetchHelper", "get", Map.of("id", 42)));
    bus.publish(new DslRunCompletedEvent(runId, "Ping", ExecutionMode.RUN, Result.success("ok"),
            correlationId));

    assertThat(sink.events).hasSize(4);
    assertThat(sink.events.get(0).eventType()).isEqualTo("run_started");
    assertThat(sink.events.get(0).correlationId()).isEqualTo(correlationId);
    assertThat(sink.events.get(1).eventType()).isEqualTo("call_start");
    assertThat(sink.events.get(1).callMetadata().callId()).isEqualTo("call_001");
    assertThat(sink.events.get(2).eventType()).isEqualTo("call_end");
    assertThat(sink.events.get(2).callMetadata().callId()).isEqualTo("call_001");
    assertThat(sink.events.get(3).eventType()).isEqualTo("run_completed");
    assertThat(sink.events.get(3).output()).isEqualTo("ok");
    assertThat(sink.closedRuns).containsExactly(runId);
  }

  @Test
  void ignoresRunsWithNonRecordableRoutes() {
    InMemorySink sink = new InMemorySink();
    VhsRecorder recorder = new VhsRecorder(sink, List.of("Other"));
    DslExecutionEventBus bus = new DslExecutionEventBus();
    bus.register(recorder);

    bus.publish(new DslRunStartedEvent("run-2", "Ping", ExecutionMode.RUN, null));
    bus.publish(new DslRunCompletedEvent("run-2", "Ping", ExecutionMode.RUN, Result.success("ok"),
            null));

    assertThat(sink.events).isEmpty();
  }

  @Test
  void wildcardRouteMatchesAnyRun() {
    InMemorySink sink = new InMemorySink();
    VhsRecorder recorder = new VhsRecorder(sink, List.of("*"));
    DslExecutionEventBus bus = new DslExecutionEventBus();
    bus.register(recorder);

    bus.publish(new DslRunStartedEvent("run-3", "Anything", ExecutionMode.RUN, null));
    bus.publish(new DslRunCompletedEvent("run-3", "Anything", ExecutionMode.RUN,
            Result.success("ok"), null));

    assertThat(sink.events).hasSize(2);
    assertThat(sink.closedRuns).containsExactly("run-3");
  }

  @Test
  void truncatedRunStillClosesTape() {
    InMemorySink sink = new InMemorySink();
    VhsRecorder recorder = new VhsRecorder(sink, List.of("Ping"));
    DslExecutionEventBus bus = new DslExecutionEventBus();
    bus.register(recorder);

    bus.publish(new DslRunStartedEvent("run-4", "Ping", ExecutionMode.RUN, null));
    bus.publish(new DslExternalCallEvent("run-4", "helper", "FailHelper", "get", null));
    bus.publish(new DslRunCompletedEvent("run-4", "Ping", ExecutionMode.RUN,
            Result.failure(new RuntimeException("boom")), null));

    assertThat(sink.events).hasSize(4);
    assertThat(sink.closedRuns).containsExactly("run-4");
  }

  @Test
  void beansAbsentWhenDisabled() {
    runner
            .withPropertyValues("cbs.vhs.enabled=false")
            .run(ctx -> {
              assertThat(ctx).doesNotHaveBean(VhsTapeSink.class);
              assertThat(ctx).doesNotHaveBean(VhsRecorder.class);
            });
  }

  @Test
  void beansPresentWhenEnabled() {
    runner
            .withPropertyValues(
                    "cbs.vhs.enabled=true",
                    "cbs.vhs.recordableRoutes=Ping",
                    "cbs.vhs.sink.local.path=${java.io.tmpdir}/vhs-test-enabled")
            .run(ctx -> {
              assertThat(ctx).hasSingleBean(VhsTapeSink.class);
              assertThat(ctx).hasSingleBean(VhsRecorder.class);
            });
  }

  @Configuration
  @Import(VhsConfiguration.class)
  static class VhsTestConfig {

    @Bean
    DslExecutionEventBus eventBus() {
      return new DslExecutionEventBus();
    }
  }

  private static final class InMemorySink implements VhsTapeSink {

    final List<TapeEvent> events = new ArrayList<>();
    final List<String> closedRuns = new ArrayList<>();

    @Override
    public void start(String runId, String route, String correlationId) {
      // no-op
    }

    @Override
    public void append(String runId, TapeEvent event) {
      events.add(event);
    }

    @Override
    public long close(String runId) {
      closedRuns.add(runId);
      return events.size();
    }
  }
}
