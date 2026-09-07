package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.entity.DslRunEntity;
import org.junit.jupiter.api.Test;

/**
 * Pins the qualified {@code dsl_runs} table name produced by
 * {@link DslRunNamingStrategy#qualifiedTableName()}.
 *
 * <p>Covers the four branches of the single naming rule (default, custom table,
 * schema only, schema + custom table) and verifies SDJ
 * {@link DslRunNamingStrategy#getTableName(Class)} delegates to it.
 */
class DslRunNamingStrategyTest {

  @Test
  void defaultsToDslRunsWhenNothingConfigured() {
    DslRunNamingStrategy strategy = new DslRunNamingStrategy(props(null, null));

    assertThat(strategy.qualifiedTableName()).isEqualTo("dsl_runs");
    assertThat(strategy.getTableName(DslRunEntity.class)).isEqualTo("dsl_runs");
  }

  @Test
  void usesCustomTableNameWhenSchemaAbsent() {
    DslRunNamingStrategy strategy = new DslRunNamingStrategy(props(null, "my_dsl_runs"));

    assertThat(strategy.qualifiedTableName()).isEqualTo("my_dsl_runs");
    assertThat(strategy.getTableName(DslRunEntity.class)).isEqualTo("my_dsl_runs");
  }

  @Test
  void qualifiesDefaultTableNameWithSchema() {
    DslRunNamingStrategy strategy = new DslRunNamingStrategy(props("cbs", null));

    assertThat(strategy.qualifiedTableName()).isEqualTo("cbs.dsl_runs");
    assertThat(strategy.getTableName(DslRunEntity.class)).isEqualTo("cbs.dsl_runs");
  }

  @Test
  void qualifiesCustomTableNameWithSchema() {
    DslRunNamingStrategy strategy = new DslRunNamingStrategy(props("cbs", "my_dsl_runs"));

    assertThat(strategy.qualifiedTableName()).isEqualTo("cbs.my_dsl_runs");
    assertThat(strategy.getTableName(DslRunEntity.class)).isEqualTo("cbs.my_dsl_runs");
  }

  private static DslRunPersistenceProperties props(String schema, String tableName) {
    return new DslRunPersistenceProperties(schema, tableName, false, null);
  }
}