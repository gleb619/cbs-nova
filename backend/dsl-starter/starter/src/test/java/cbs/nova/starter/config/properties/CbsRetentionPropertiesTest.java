package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class CbsRetentionPropertiesTest {

  @Test
  void defaultsAreOff() {
    CbsRetentionProperties props = bind(Map.of());

    assertThat(props.getAudit().getRetention()).isEqualTo(Duration.ZERO);
    assertThat(props.getEvents().getRetention()).isEqualTo(Duration.ZERO);
    assertThat(props.getDiagnostics().getRetention()).isEqualTo(Duration.ZERO);
    assertThat(props.getAudit().getPurgeInterval()).isEqualTo(Duration.ofHours(1));
    assertThat(props.getEvents().getPurgeBatchSize()).isEqualTo(500);
    assertThat(props.getDiagnostics().getPurgeBatchSize()).isEqualTo(500);
  }

  @Test
  void retentionBindsPerTable() {
    Map<String, String> source = new HashMap<>();
    source.put("cbs.audit.retention", "P90D");
    source.put("cbs.events.retention", "P30D");
    source.put("cbs.diagnostics.retention", "P7D");

    CbsRetentionProperties props = bind(source);

    assertThat(props.getAudit().getRetention()).isEqualTo(Duration.ofDays(90));
    assertThat(props.getEvents().getRetention()).isEqualTo(Duration.ofDays(30));
    assertThat(props.getDiagnostics().getRetention()).isEqualTo(Duration.ofDays(7));
  }

  @Test
  void purgeIntervalAndBatchSizeBindPerTable() {
    Map<String, String> source = new HashMap<>();
    source.put("cbs.audit.purge-interval", "PT2H");
    source.put("cbs.audit.purge-batch-size", "100");
    source.put("cbs.events.purge-interval", "PT30M");
    source.put("cbs.events.purge-batch-size", "250");
    source.put("cbs.diagnostics.purge-interval", "PT15M");
    source.put("cbs.diagnostics.purge-batch-size", "50");

    CbsRetentionProperties props = bind(source);

    assertThat(props.getAudit().getPurgeInterval()).isEqualTo(Duration.ofHours(2));
    assertThat(props.getAudit().getPurgeBatchSize()).isEqualTo(100);
    assertThat(props.getEvents().getPurgeInterval()).isEqualTo(Duration.ofMinutes(30));
    assertThat(props.getEvents().getPurgeBatchSize()).isEqualTo(250);
    assertThat(props.getDiagnostics().getPurgeInterval()).isEqualTo(Duration.ofMinutes(15));
    assertThat(props.getDiagnostics().getPurgeBatchSize()).isEqualTo(50);
  }

  private static CbsRetentionProperties bind(Map<String, String> source) {
    return new Binder(new MapConfigurationPropertySource(source))
            .bindOrCreate("cbs", CbsRetentionProperties.class);
  }
}
