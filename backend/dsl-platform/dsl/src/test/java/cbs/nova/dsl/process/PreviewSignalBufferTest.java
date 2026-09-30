package cbs.nova.dsl.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Pins the deterministic preview signal-buffer contract (T736): seeded payload resolves at the
 * await point, unseeded names fail fast with a dedicated exception (never a hang, never the generic
 * "missing SignalAwaiter" IllegalStateException).
 */
class PreviewSignalBufferTest {

  @Test
  void awaitSignalReturnsSeededPayloadAtAwaitPoint() {
    var buffer = new PreviewSignalBuffer(
            List.of(new SeededSignal("approval", "{\"approved\":true,\"reviewer\":\"alice\"}",
                    null)));

    Map<String, Object> payload = buffer.awaitSignal("approval", Map.class);

    assertThat(payload).containsEntry("approved", true).containsEntry("reviewer", "alice");
    assertThat(buffer.awaitIndex()).isEqualTo(1);
  }

  @Test
  void signalReceivedAndPayloadAreVisibleAfterDelivery() {
    var buffer = new PreviewSignalBuffer(List.of(
            new SeededSignal("approval", "{\"approved\":true}", 1),
            SeededSignal.of("other-seeded", "{}")));

    assertThat(buffer.signalReceived("approval")).isFalse();
    assertThat(buffer.getSignalPayload("approval", Map.class)).isNull();

    buffer.awaitSignal("other-seeded", Map.class);

    assertThat(buffer.awaitIndex()).isEqualTo(1);
    assertThat(buffer.signalReceived("approval")).isTrue();
    assertThat(buffer.getSignalPayload("approval", Map.class)).containsEntry("approved", true);
  }

  @Test
  void deliverAtZeroIsVisibleBeforeFirstAwait() {
    var buffer = new PreviewSignalBuffer(
            List.of(SeededSignal.of("approval", "{\"approved\":false}")));

    assertThat(buffer.signalReceived("approval")).isTrue();
    assertThat(buffer.getSignalPayload("approval", Map.class)).containsEntry("approved", false);
  }

  @Test
  void unseededAwaitFailsFastWithSignalNotSeeded() {
    var buffer = new PreviewSignalBuffer(List.of(SeededSignal.of("approval", "{}")));

    assertThatThrownBy(() -> buffer.awaitSignal("missing", Map.class))
            .isInstanceOf(SignalNotSeededException.class)
            .isNotInstanceOf(IllegalStateException.class)
            .hasMessageContaining("missing");
  }

  @Test
  void unseededNameIsAbsentForPayloadQueries() {
    var buffer = PreviewSignalBuffer.empty();

    assertThat(buffer.signalReceived("approval")).isFalse();
    assertThat(buffer.getSignalPayload("approval", Map.class)).isNull();
    assertThat(buffer.snapshot()).isEmpty();
  }

  @Test
  void seededPayloadConvertsToDeclaredRecordType() {
    var buffer = new PreviewSignalBuffer(
            List.of(new SeededSignal("decision", "{\"amount\":42,\"label\":\"ok\"}", null)));

    Decision decision = buffer.awaitSignal("decision", Decision.class);

    assertThat(decision.amount()).isEqualTo(42);
    assertThat(decision.label()).isEqualTo("ok");
  }

  @Test
  void incompatibleSeededPayloadFailsWithTypeMismatch() {
    var buffer = new PreviewSignalBuffer(
            List.of(new SeededSignal("decision", "\"plain-string\"", null)));

    assertThatThrownBy(() -> buffer.awaitSignal("decision", Decision.class))
            .isInstanceOf(SignalPayloadTypeMismatchException.class)
            .hasMessageContaining("decision");
  }

  @Test
  void snapshotExposesDeliveredPayloads() {
    var buffer = new PreviewSignalBuffer(
            List.of(SeededSignal.of("approval", "{\"approved\":true}")));

    buffer.awaitSignal("approval", Map.class);

    assertThat(buffer.snapshot()).containsKey("approval");
    assertThat(buffer.timelineSummary()).containsKey("approval");
  }

  record Decision(int amount, String label) {
  }
}
