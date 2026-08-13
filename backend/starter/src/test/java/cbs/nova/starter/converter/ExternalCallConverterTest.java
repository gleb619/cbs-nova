package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.core.recorder.ExternalCall;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class ExternalCallConverterTest {

  @Test
  void toCallJsonPreservesReportShape() {
    ExternalCall call = new ExternalCall(
            "http",
            "https://example.com",
            "POST",
            1234567890L,
            Map.of("requestBody", Map.of("id", 1), "status", 200));

    List<Map<String, Object>> callsJson = ExternalCallConverter.toCallJson(List.of(call));

    assertThat(callsJson).hasSize(1);
    Map<String, Object> map = callsJson.get(0);
    assertThat(map).containsEntry("type", "http");
    assertThat(map).containsEntry("target", "https://example.com");
    assertThat(map).containsEntry("operation", "POST");
    assertThat(map).containsEntry("timestamp", 1234567890L);
    assertThat(map).containsKey("metadata");
    @SuppressWarnings("unchecked")
    Map<String, Object> metadata = (Map<String, Object>) map.get("metadata");
    assertThat(metadata).containsEntry("status", 200);
    assertThat(metadata).containsKey("requestBody");
  }

  @Test
  void toCallCountsAggregatesByType() {
    ExternalCall http = new ExternalCall("http", "x", "GET", 1L, Map.of());
    ExternalCall jdbc = new ExternalCall("jdbc", "y", "SELECT", 2L, Map.of());
    ExternalCall http2 = new ExternalCall("http", "z", "POST", 3L, Map.of());

    Map<String, Integer> counts = ExternalCallConverter.toCallCounts(List.of(http, jdbc, http2));

    assertThat(counts).containsEntry("http", 2);
    assertThat(counts).containsEntry("jdbc", 1);
  }
}
