package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class ExternalCallTrackerTest {

  private ExternalCallTracker tracker;

  @BeforeEach
  void setUp() {
    tracker = new ExternalCallTracker();
    tracker.resetGlobalCounts();
    tracker.stopTracking();
  }

  @AfterEach
  void tearDown() {
    tracker.stopTracking();
    tracker.resetGlobalCounts();
  }

  @Test
  void startTrackingIsolatesCallsPerThread() {
    var calls = new ArrayList<ExternalCallTracker.CallDetail>();
    tracker.startTracking(calls);
    tracker.record("http", "svc", "GET", null);
    tracker.stopTracking();
    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).type()).isEqualTo(ExternalCallTracker.TYPE_HTTP);
  }

  @Test
  void stopTrackingClearsThreadLocal() {
    tracker.startTracking(new ArrayList<>());
    tracker.stopTracking();
    assertThat(tracker.getActiveTracking()).isNull();
  }

  @Test
  void recordIncrementsGlobalCount() {
    tracker.record("jdbc", "db", "SELECT", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_DATABASE)).isEqualTo(1);
  }

  @Test
  void recordNotifiesListeners() {
    var count = new AtomicInteger(0);
    tracker.registerListener((type, target, op, payload) -> count.incrementAndGet());
    tracker.record("kafka", "topic", "send", null);
    assertThat(count.get()).isEqualTo(1);
  }

  @Test
  void resetGlobalCountsClearsAggregation() {
    tracker.record("rest", "api", "POST", null);
    tracker.resetGlobalCounts();
    assertThat(tracker.getGlobalCounts()).isEmpty();
  }

  @Test
  void normalizeTypeMapsDatabaseAliases() {
    List<String> aliases = List.of("jdbc", "db", "sql", "hibernate", "jpa", "datasource");
    for (String alias : aliases) {
      tracker.record(alias, "t", "op", null);
    }
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_DATABASE))
            .isEqualTo(aliases.size());
  }

  @Test
  void normalizeTypeMapsHttpAliases() {
    tracker.record("rest", "t", "op", null);
    tracker.record("http", "t", "op", null);
    tracker.record("resttemplate", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_HTTP)).isEqualTo(3);
  }

  @Test
  void normalizeTypeMapsMqAliases() {
    tracker.record("kafka", "t", "op", null);
    tracker.record("jms", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_MQ)).isEqualTo(2);
  }

  @Test
  void normalizeTypeMapsGrpcToMicroservice() {
    tracker.record("grpc", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_MICROSERVICE)).isEqualTo(1);
  }

  @Test
  void normalizeTypeMapsApiToExternalApi() {
    tracker.record("api", "t", "op", null);
    assertThat(tracker.getGlobalCounts().get(ExternalCallTracker.TYPE_EXTERNAL_API)).isEqualTo(1);
  }

  @Test
  void startMockingFindMockStopMockingRoundTrip() {
    var mocks = Map.of("activity:MyActivity:invoke", (Object) "mocked");
    tracker.startMocking(mocks);
    assertThat(tracker.findMock("activity", "MyActivity", "invoke")).isEqualTo("mocked");
    tracker.stopMocking();
    assertThat(tracker.findMock("activity", "MyActivity", "invoke")).isNull();
  }

  @Test
  void findMockNormalizesTypeLikeRecord() {
    var mocks = Map.of("activity:MyActivity:invoke", (Object) "mocked");
    tracker.startMocking(mocks);
    assertThat(tracker.findMock("ACTIVITY", "MyActivity", "invoke")).isEqualTo("mocked");
    assertThat(tracker.findMock("temporal", "MyActivity", "invoke")).isEqualTo("mocked");
    tracker.stopMocking();
  }

  @Test
  void findMockReturnsNullWhenNoMockRegistered() {
    tracker.startMocking(Map.of());
    assertThat(tracker.findMock("activity", "MyActivity", "invoke")).isNull();
    tracker.stopMocking();
  }

  @Test
  void recordMarksMockAppliedForActivity() {
    var calls = new ArrayList<ExternalCallTracker.CallDetail>();
    tracker.startTracking(calls);
    tracker.startMocking(Map.of("activity:MyActivity:execute", (Object) "result"));
    tracker.record("activity", "MyActivity", "execute", null);
    tracker.stopMocking();
    tracker.stopTracking();

    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).metadata()).containsEntry("mockApplied", true);
  }

  @Test
  void recordMarksMockConfiguredForDatabase() {
    var calls = new ArrayList<ExternalCallTracker.CallDetail>();
    tracker.startTracking(calls);
    tracker.startMocking(Map.of("database:jdbc:SELECT", (Object) "result"));
    tracker.record("jdbc", "jdbc", "SELECT", null);
    tracker.stopMocking();
    tracker.stopTracking();

    assertThat(calls).hasSize(1);
    assertThat(calls.get(0).metadata()).containsEntry("mockConfigured", true)
            .containsEntry("mockApplied", false);
  }
}
