package cbs.nova.starter.core.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import cbs.nova.starter.logging.ThreadLocalDryRunLoggingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

class RunIdKeyedExternalCallRecorderTest {

  private ThreadLocalDryRunLoggingContext dryRunLoggingContext;
  private RunIdKeyedExternalCallRecorder recorder;

  @BeforeEach
  void setUp() {
    dryRunLoggingContext = new ThreadLocalDryRunLoggingContext();
    recorder = new RunIdKeyedExternalCallRecorder(dryRunLoggingContext, null);
    recorder.resetGlobalCounts();
  }

  @AfterEach
  void tearDown() {
    recorder.resetGlobalCounts();
  }

  @Test
  void startRunIsolatesCallsPerRun() {
    recorder.startRun("r1");
    recorder.record("http", "svc", "GET", null);
    List<ExternalCall> calls = recorder.finishRun("r1");
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallRecorder.TYPE_HTTP);
  }

  @Test
  void finishRunRemovesRunState() {
    recorder.startRun("r1");
    recorder.record("http", "svc", "GET", null);
    assertThat(recorder.finishRun("r1")).hasSize(1);
    assertThat(recorder.finishRun("r1")).isEmpty();
  }

  @Test
  void startRunResetsStateForSameRunId() {
    recorder.startRun("r1");
    recorder.record("http", "svc", "GET", null);
    recorder.finishRun("r1");
    recorder.startRun("r2");
    recorder.record("database", "db", "SELECT", null);
    List<ExternalCall> calls = recorder.finishRun("r2");
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallRecorder.TYPE_DATABASE);
  }

  @Test
  void recordIncrementsGlobalCount() {
    recorder.startRun("r1");
    recorder.record("jdbc", "db", "SELECT", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_DATABASE)).isEqualTo(1);
  }

  @Test
  void recordNotifiesListeners() {
    AtomicInteger count = new AtomicInteger(0);
    recorder.registerListener(event -> {
      if (event instanceof DslExternalCallEvent) {
        count.incrementAndGet();
      }
    });
    recorder.startRun("r1");
    recorder.record("kafka", "topic", "send", null);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  void resetGlobalCountsClearsAggregation() {
    recorder.startRun("r1");
    recorder.record("rest", "api", "POST", null);
    recorder.resetGlobalCounts();
    assertThat(recorder.getGlobalCounts()).isEmpty();
  }

  @Test
  void normalizeTypeMapsDatabaseAliases() {
    recorder.startRun("r1");
    List<String> aliases = List.of("jdbc", "db", "sql", "hibernate", "jpa", "datasource");
    for (String alias : aliases) {
      recorder.record(alias, "t", "op", null);
    }
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_DATABASE))
            .isEqualTo(aliases.size());
  }

  @Test
  void normalizeTypeMapsHttpAliases() {
    recorder.startRun("r1");
    recorder.record("rest", "t", "op", null);
    recorder.record("http", "t", "op", null);
    recorder.record("resttemplate", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_HTTP)).isEqualTo(3);
  }

  @Test
  void normalizeTypeMapsMqAliases() {
    recorder.startRun("r1");
    recorder.record("kafka", "t", "op", null);
    recorder.record("jms", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_MQ)).isEqualTo(2);
  }

  @Test
  void normalizeTypeMapsGrpcToMicroservice() {
    recorder.startRun("r1");
    recorder.record("grpc", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_MICROSERVICE)).isEqualTo(1);
  }

  @Test
  void normalizeTypeMapsApiToExternalApi() {
    recorder.startRun("r1");
    recorder.record("api", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_EXTERNAL_API)).isEqualTo(1);
  }

  @Test
  void recordCapturesPayloadMetadataWithoutMockMarkers() {
    recorder.startRun("r1");
    recorder.record("activity", "MyActivity", "execute", Map.of("result", "value"));
    List<ExternalCall> calls = recorder.finishRun("r1");

    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).metadata()).containsOnlyKeys("payload");
  }

  @Test
  void recordFromDifferentThreadAttributesToStartingRun() throws Exception {
    recorder.startRun("r1");
    // Simulate the initiator thread moving on; the propagator will restore the runId on worker
    // threads before they record.
    dryRunLoggingContext.clearRunId();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      Future<?> future = executor.submit(() -> {
        dryRunLoggingContext.setRunId("r1");
        try {
          recorder.record("http", "svc", "GET", null);
        } finally {
          dryRunLoggingContext.clearRunId();
        }
      });
      future.get();
    } finally {
      executor.shutdown();
      assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    List<ExternalCall> calls = recorder.finishRun("r1");
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallRecorder.TYPE_HTTP);
  }

  @Test
  void recordKeepsOnlyMostRecentCallsPerRun() {
    recorder.startRun("run-1");
    for (int i = 1; i <= 101; i++) {
      recorder.record("http", "svc", "GET-" + i, null);
    }

    List<ExternalCall> calls = recorder.finishRun("run-1");

    assertThat(calls).hasSize(100);
    assertThat(calls.get(0).operation()).isEqualTo("GET-2");
    assertThat(calls.get(99).operation()).isEqualTo("GET-101");
  }

  @Test
  void retainsOnlyLastHundredTrackedRuns() {
    for (int i = 1; i <= 101; i++) {
      recorder.startRun("run-" + i);
      recorder.record("http", "svc", "GET", null);
    }

    assertThat(recorder.finishRun("run-1")).isEmpty();
    assertThat(recorder.finishRun("run-2")).hasSize(1);
    assertThat(recorder.finishRun("run-101")).hasSize(1);
  }
}
