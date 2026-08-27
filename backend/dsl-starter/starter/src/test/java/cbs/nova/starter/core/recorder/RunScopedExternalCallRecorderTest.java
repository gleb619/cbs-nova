package cbs.nova.starter.core.recorder;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.event.DslExecutionEvent.DslExternalCallEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class RunScopedExternalCallRecorderTest {

  private RunScopedExternalCallRecorder recorder;

  @BeforeEach
  void setUp() {
    recorder = new RunScopedExternalCallRecorder(null);
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
  void finishRunClearsThreadLocal() {
    recorder.startRun("r1");
    recorder.record("http", "svc", "GET", null);
    recorder.finishRun("r1");
    recorder.startRun("r2");
    List<ExternalCall> calls = recorder.finishRun("r2");
    assertThat(calls).isEmpty();
  }

  @Test
  void recordIncrementsGlobalCount() {
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
    recorder.record("kafka", "topic", "send", null);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  void resetGlobalCountsClearsAggregation() {
    recorder.record("rest", "api", "POST", null);
    recorder.resetGlobalCounts();
    assertThat(recorder.getGlobalCounts()).isEmpty();
  }

  @Test
  void normalizeTypeMapsDatabaseAliases() {
    List<String> aliases = List.of("jdbc", "db", "sql", "hibernate", "jpa", "datasource");
    for (String alias : aliases) {
      recorder.record(alias, "t", "op", null);
    }
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_DATABASE))
            .isEqualTo(aliases.size());
  }

  @Test
  void normalizeTypeMapsHttpAliases() {
    recorder.record("rest", "t", "op", null);
    recorder.record("http", "t", "op", null);
    recorder.record("resttemplate", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_HTTP)).isEqualTo(3);
  }

  @Test
  void normalizeTypeMapsMqAliases() {
    recorder.record("kafka", "t", "op", null);
    recorder.record("jms", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_MQ)).isEqualTo(2);
  }

  @Test
  void normalizeTypeMapsGrpcToMicroservice() {
    recorder.record("grpc", "t", "op", null);
    assertThat(recorder.getGlobalCounts().get(ExternalCallRecorder.TYPE_MICROSERVICE)).isEqualTo(1);
  }

  @Test
  void normalizeTypeMapsApiToExternalApi() {
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
}
