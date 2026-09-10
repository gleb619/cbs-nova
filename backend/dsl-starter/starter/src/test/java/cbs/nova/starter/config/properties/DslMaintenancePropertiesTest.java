package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Verifies the {@code dsl.maintenance.*} property binding shape:
 *
 * <ul>
 * <li>sensible defaults</li>
 * <li>per-task enable flags</li>
 * <li>the rollout flag binding</li>
 * </ul>
 */
class DslMaintenancePropertiesTest {

  @Test
  void defaultsAreSensible() {
    DslMaintenanceProperties props = bind(Map.of());
    assertThat(props.unifiedEnabled()).isFalse();
    assertThat(props.schedule()).isEqualTo(Duration.ofHours(1));
    assertThat(props.tasks().runRetention().enabled()).isTrue();
    assertThat(props.tasks().orphans().enabled()).isTrue();
    assertThat(props.tasks().auditRetention().enabled()).isTrue();
  }

  @Test
  void unifiedEnabledBinds() {
    DslMaintenanceProperties props = bind(Map.of("dsl.maintenance.unified-enabled", "true"));
    assertThat(props.unifiedEnabled()).isTrue();
  }

  @Test
  void scheduleBinds() {
    DslMaintenanceProperties props = bind(Map.of("dsl.maintenance.schedule", "PT15M"));
    assertThat(props.schedule()).isEqualTo(Duration.ofMinutes(15));
  }

  @Test
  void perTaskEnabledFlagsBind() {
    Map<String, String> source = new HashMap<>();
    source.put("dsl.maintenance.tasks.run-retention.enabled", "false");
    source.put("dsl.maintenance.tasks.orphans.enabled", "false");
    source.put("dsl.maintenance.tasks.audit-retention.enabled", "false");
    DslMaintenanceProperties props = bind(source);
    assertThat(props.tasks().runRetention().enabled()).isFalse();
    assertThat(props.tasks().orphans().enabled()).isFalse();
    assertThat(props.tasks().auditRetention().enabled()).isFalse();
  }

  private static DslMaintenanceProperties bind(Map<String, String> source) {
    return new Binder(new MapConfigurationPropertySource(source))
            .bindOrCreate("dsl.maintenance", DslMaintenanceProperties.class);
  }
}
