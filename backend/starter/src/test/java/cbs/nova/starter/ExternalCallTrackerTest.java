package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class ExternalCallTrackerTest {

  private ExternalCallTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    ExternalCallTracker.stopTracking();
  }

  @AfterEach
  void tearDown() {
    ExternalCallTracker.stopTracking();
    tracker.resetGlobalCounts();
    ExternalCallTracker.instance = null;
  }

  @Test
  void startTrackingIsolatesCallsPerThread() {
    var calls = new ArrayList<ExternalCallTracker.CallDetail>();
    ExternalCallTracker.startTracking(calls);
    tracker.recordCall("http", "svc", "GET", null);
    ExternalCallTracker.stopTracking();
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallTracker.TYPE_HTTP);
  }

  @Test
  void stopTrackingClearsThreadLocal() {
    ExternalCallTracker.startTracking(new ArrayList<>());
    ExternalCallTracker.stopTracking();
    assertThat(ExternalCallTracker.getActiveTracking()).isNull();
  }

  @Test
  void recordCallIncrementsGlobalCount() {
    tracker.recordCall("jdbc", "db", "SELECT", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_DATABASE)).isEqualTo(1);
  }

  @Test
  void recordCallNotifiesListeners() {
    var count = new AtomicInteger(0);
    tracker.registerListener((type, target, op, payload) -> count.incrementAndGet());
    tracker.recordCall("kafka", "topic", "send", null);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  void staticRecordWritesToThreadLocalWhenNoInstance() {
    ExternalCallTracker.instance = null;
    var calls = new ArrayList<ExternalCallTracker.CallDetail>();
    ExternalCallTracker.startTracking(calls);
    ExternalCallTracker.record("file", "path", "read", null);
    ExternalCallTracker.stopTracking();
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallTracker.TYPE_FILE_SYSTEM);
  }

  @Test
  void resetGlobalCountsClearsAggregation() {
    tracker.recordCall("rest", "api", "POST", null);
    tracker.resetGlobalCounts();
    assertThat(tracker.getGlobalCounts()).isEmpty();
  }

  @Test
  void normalizeTypeMapsDatabaseAliases() {
    List<String> aliases = List.of("jdbc", "db", "sql", "hibernate", "jpa", "datasource");
    for (String alias : aliases) {
      tracker.recordCall(alias, "t", "op", null);
    }
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_DATABASE))
            .isEqualTo(aliases.size());
  }

  @Test
  void normalizeTypeMapsHttpAliases() {
    tracker.recordCall("rest", "t", "op", null);
    tracker.recordCall("http", "t", "op", null);
    tracker.recordCall("resttemplate", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_HTTP)).isEqualTo(3);
  }

  @Test
  void normalizeTypeMapsMqAliases() {
    tracker.recordCall("kafka", "t", "op", null);
    tracker.recordCall("jms", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_MQ)).isEqualTo(2);
  }

  @Test
  void normalizeTypeMapsGrpcToMicroservice() {
    tracker.recordCall("grpc", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_MICROSERVICE)).isEqualTo(1);
  }

  @Test
  void normalizeTypeMapsApiToExternalApi() {
    tracker.recordCall("api", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_EXTERNAL_API)).isEqualTo(1);
  }

  @Test
  void normalizeTypeDefaultsToOther() {
    tracker.recordCall("unknown_xyz", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_OTHER)).isEqualTo(1);
  }
}
