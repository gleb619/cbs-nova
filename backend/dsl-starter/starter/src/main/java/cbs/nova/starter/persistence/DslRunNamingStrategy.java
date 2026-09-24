package cbs.nova.starter.persistence;

import static cbs.nova.starter.core.StarterConstants.DSL_RUNS_DEFAULT_TABLE;

import cbs.nova.starter.config.properties.DslRunPersistenceProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.DslRunEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.data.relational.core.mapping.NamingStrategy;

/**
 * Naming strategy that makes the {@link DslRunEntity} table name and schema configurable.
 */
@RequiredArgsConstructor
public class DslRunNamingStrategy implements NamingStrategy {

  private final DslRunPersistenceProperties properties;

  @Override
  public String getTableName(Class<?> type) {
    if (DslRunEntity.class.equals(type)) {
      return qualifiedTableName();
    }
    return NamingStrategy.super.getTableName(type);
  }

  /**
   * Returns the schema-qualified table name for the {@code dsl_runs} table.
   *
   * <p>
   * Single source of truth shared by SDJ entity mapping ({@link #getTableName(Class)}) and raw-JDBC
   * consumers (e.g. {@link JdbcDslRunRepository}).
   */
  public String qualifiedTableName() {
    String table = properties.tableName() != null && !properties.tableName().isBlank()
            ? properties.tableName()
            : DSL_RUNS_DEFAULT_TABLE;
    String schema = properties.schema();
    return (schema != null && !schema.isBlank()) ? schema + "." + table : table;
  }
}
